package cn.com.keelbase.export;

import cn.com.keelbase.annotation.KeelbaseTool;
import cn.com.keelbase.annotation.KeelbaseRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 扫描带 {@code @KeelbaseTool} 的 Spring MVC handler 方法，推断为代理工具配置。
 *
 * <p>口径对齐 KeelBase 生成器（scripts/generator/import-openapi-proxy.mjs）：
 * 工具名 camelCase→snake_case、类型映射（{@link TypeMapper}）、path 占位参数清洗、
 * 读 GET=R1 / 写 POST·PUT·PATCH·DELETE=R3、header 参数跳过、名称冲突去重。
 */
public class ProxyToolsScanner {

    private static final Logger log = LoggerFactory.getLogger(ProxyToolsScanner.class);

    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<Class<?>> SKIPPED_PARAM_TYPES = List.of(
            org.springframework.ui.Model.class,
            jakarta.servlet.ServletRequest.class,
            jakarta.servlet.ServletResponse.class,
            jakarta.servlet.http.HttpServletRequest.class,
            jakarta.servlet.http.HttpServletResponse.class,
            java.security.Principal.class);

    private final RequestMappingHandlerMapping mapping;

    public ProxyToolsScanner(RequestMappingHandlerMapping mapping) {
        this.mapping = mapping;
    }

    public List<ProxyToolItem> scan() {
        List<ProxyToolItem> tools = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = e.getValue();
            KeelbaseTool ann = handler.getMethodAnnotation(KeelbaseTool.class);
            if (ann == null) {
                continue;
            }
            ProxyToolItem tool = buildTool(e.getKey(), handler, ann);
            if (tool == null) {
                continue;
            }
            String name = tool.name();
            if (seenNames.contains(name)) {
                int i = 2;
                while (seenNames.contains(name + "_" + i)) {
                    i += 1;
                }
                log.warn("keelbase 工具名冲突：'{}' 已自动改名为 '{}'（建议显式 @KeelbaseTool(name=...) 避免歧义）",
                        name, name + "_" + i);
                name = name + "_" + i;
                tool = new ProxyToolItem(name, tool.description(), tool.method(), tool.path(),
                        tool.parameters(), tool.queryParams(), tool.riskLevel(), tool.revokePath());
            }
            seenNames.add(name);
            tools.add(tool);
        }
        return tools;
    }

    private ProxyToolItem buildTool(RequestMappingInfo info, HandlerMethod handler, KeelbaseTool ann) {
        String method = info.getMethodsCondition().getMethods().stream()
                .findFirst().map(RequestMethod::name).orElse(null);
        // Spring 6.1 默认 PathPattern：getPatternsCondition() 可能为 null，须回退 getPathPatternsCondition()
        String path = null;
        if (info.getPathPatternsCondition() != null) {
            path = info.getPathPatternsCondition().getPatternValues().stream()
                    .findFirst().orElse(null);
        } else if (info.getPatternsCondition() != null) {
            path = info.getPatternsCondition().getPatterns().stream()
                    .findFirst().orElse(null);
        }
        if (method == null || path == null) {
            log.warn("keelbase 工具跳过 {}#{}: 无法解析 HTTP method/path（检查 @RequestMapping 映射是否完整）",
                    handler.getMethod().getDeclaringClass().getSimpleName(), handler.getMethod().getName());
            return null;
        }
        boolean write = WRITE_METHODS.contains(method);

        String name = ann.name().isBlank() ? camelToSnake(handler.getMethod().getName()) : ann.name();
        if (name == null || !name.matches("^[a-z][a-z0-9_]{0,39}$")) {
            log.warn("keelbase 工具跳过 {}#{}: 工具名 '{}' 非法（需 ^[a-z][a-z0-9_]{{0,39}}$；缺省由方法名 camelCase→snake_case）",
                    handler.getMethod().getDeclaringClass().getSimpleName(), handler.getMethod().getName(), name);
            return null;
        }
        String description = ann.description().isBlank() ? method + " " + path : ann.description();
        String riskLevel = resolveRiskLevel(ann.riskLevel(), write);
        String revokePath = ann.revokePath().isBlank() ? null : ann.revokePath();

        List<ToolParameter> parameters = new ArrayList<>();
        List<String> queryParams = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // HandlerMethod.getMethodParameters() 的 MethodParameter 不带 ParameterNameDiscoverer，
        // 不注入则 mp.getParameterName() 恒为 null，@RequestParam/@PathVariable（未显式 name）会整体丢失。
        DefaultParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();
        for (MethodParameter mp : handler.getMethodParameters()) {
            mp.initParameterNameDiscovery(paramNameDiscoverer);
            PathVariable pv = mp.getParameterAnnotation(PathVariable.class);
            RequestParam rp = mp.getParameterAnnotation(RequestParam.class);
            RequestBody rb = mp.getParameterAnnotation(RequestBody.class);
            if (pv != null) {
                String pname = sanitizeParamName(shortName(pv.name(), mp));
                if (pname != null && seen.add(pname)) {
                    parameters.add(new ToolParameter(pname, TypeMapper.map(mp.getParameterType()), "", true));
                }
            } else if (rp != null) {
                String pname = sanitizeParamName(shortName(rp.name(), mp));
                if (pname == null || !seen.add(pname)) {
                    continue;
                }
                // 必填 = @RequestParam 默认必填 且 未显式设置 defaultValue（设置缺省值即视为可选）
                boolean required = rp.required()
                        && org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE
                                .equals(rp.defaultValue());
                parameters.add(new ToolParameter(pname, TypeMapper.map(mp.getParameterType()), "", required));
                if (write) {
                    queryParams.add(pname);
                }
            } else if (rb != null && mp.getParameterType() != null) {
                for (RequestBodyFields.BodyField bf : RequestBodyFields.of(mp.getParameterType())) {
                    String fname = sanitizeParamName(bf.name());
                    if (fname == null || !seen.add(fname)) {
                        continue;
                    }
                    parameters.add(new ToolParameter(fname, bf.type(), bf.description(), bf.required()));
                }
            } else if (!SKIPPED_PARAM_TYPES.contains(mp.getParameterType())) {
                // 其他未知参数（如 @DelegationUser 自定义对象）跳过
            }
        }
        return new ProxyToolItem(name, description, method, path, parameters, queryParams,
                riskLevel, revokePath);
    }

    private static String shortName(String annName, MethodParameter mp) {
        return annName.isBlank() ? mp.getParameterName() : annName;
    }

    private static String resolveRiskLevel(KeelbaseRiskLevel level, boolean write) {
        if (level == KeelbaseRiskLevel.AUTO) {
            return write ? "R3" : "R1";
        }
        return level.name();
    }

    /** 对齐生成器 sanitizeToolName：camelCase → snake_case。 */
    static String camelToSnake(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String snake = s.replaceAll("([A-Z])", "_$1").toLowerCase().replaceAll("^_", "");
        snake = snake.replaceAll("[^a-z0-9_]", "_");
        return snake.matches("^[a-z][a-z0-9_]{0,39}$") ? snake : null;
    }

    /** 对齐生成器 sanitizeParamName：非法返回 null。 */
    static String sanitizeParamName(String s) {
        if (s == null) {
            return null;
        }
        String n = s.replaceAll("[-\\s]", "_");
        return n.matches("^[a-z][a-zA-Z0-9_]{0,29}$") ? n : null;
    }
}
