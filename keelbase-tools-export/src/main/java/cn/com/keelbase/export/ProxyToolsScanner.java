// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.export;

import cn.com.keelbase.annotation.KeelbaseTool;
import cn.com.keelbase.annotation.KeelbaseRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 扫描带 {@code @KeelbaseTool} 的 Spring MVC handler 方法，推断为代理工具配置。
 *
 * <p>口径对齐 KeelBase 生成器（scripts/generator/import-openapi-proxy.mjs）：
 * 工具名 camelCase→snake_case、类型映射（{@link TypeMapper}）、path 占位参数清洗、
 * 读 GET=R1 / 写 POST·PUT·PATCH·DELETE=R3、header 参数跳过、名称冲突去重。
 */
public class ProxyToolsScanner {

    private static final Logger log = LoggerFactory.getLogger(ProxyToolsScanner.class);

    private final SwaggerDocExtractor swaggerDoc = new SwaggerDocExtractor();
    private final JavadocDocExtractor javadocDoc = new JavadocDocExtractor();

    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<Class<?>> SKIPPED_PARAM_TYPES = List.of(
            org.springframework.ui.Model.class,
            jakarta.servlet.ServletRequest.class,
            jakarta.servlet.ServletResponse.class,
            jakarta.servlet.http.HttpServletRequest.class,
            jakarta.servlet.http.HttpServletResponse.class,
            jakarta.servlet.http.HttpSession.class,
            org.springframework.web.context.request.WebRequest.class,
            org.springframework.web.context.request.NativeWebRequest.class,
            org.springframework.web.multipart.MultipartFile.class,
            java.security.Principal.class,
            java.util.Locale.class,
            java.util.TimeZone.class,
            java.time.ZoneId.class,
            java.io.Reader.class,
            java.io.Writer.class,
            java.io.InputStream.class,
            java.io.OutputStream.class);

    private final RequestMappingHandlerMapping mapping;

    public ProxyToolsScanner(RequestMappingHandlerMapping mapping) {
        this.mapping = mapping;
    }

    public List<ProxyToolItem> scan() {
        return scanWithReport().items();
    }

    /**
     * Scan result plus the skipped and inferred-warning lists (fail-fast validation / diagnostics).
     * {@code warnings} only carries zero-boilerplate inference findings ({@code infer=true}); the
     * two-argument constructor keeps existing callers source-compatible.
     *
     * <p>扫描结果 + 被跳过清单 + 推断告警清单（fail-fast 校验 / 诊断用）。{@code warnings}
     * 只承载零样板推断发现（{@code infer=true}）；两参构造器保持既有调用方源码兼容。
     */
    public record ScanReport(List<ProxyToolItem> items, List<String> skipped, List<String> warnings) {

        public ScanReport(List<ProxyToolItem> items, List<String> skipped) {
            this(items, skipped, List.of());
        }
    }

    public ScanReport scanWithReport() {
        List<ProxyToolItem> tools = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = e.getValue();
            KeelbaseTool ann = resolveToolAnnotation(handler);
            if (ann == null) {
                continue;
            }
            ProxyToolItem tool = buildTool(e.getKey(), handler, ann, skipped, warnings);
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
        // 按工具名排序：导出确定性（mapping.getHandlerMethods() 顺序不稳定，配置 diff/审计友好）
        tools.sort(Comparator.comparing(ProxyToolItem::name));
        return new ScanReport(tools, List.copyOf(skipped), List.copyOf(warnings));
    }

    /**
     * 解析工具注解：方法级优先（enabled=false 明确排除）；无方法级 → 类级
     * {@code @KeelbaseTool}（整个 controller 一键工具化），类级 enabled=false 同样跳过。
     */
    private static KeelbaseTool resolveToolAnnotation(HandlerMethod handler) {
        KeelbaseTool methodAnn = handler.getMethodAnnotation(KeelbaseTool.class);
        if (methodAnn != null) {
            return methodAnn.enabled() ? methodAnn : null;
        }
        KeelbaseTool typeAnn = handler.getBeanType().getAnnotation(KeelbaseTool.class);
        return typeAnn != null && typeAnn.enabled() ? typeAnn : null;
    }

    private ProxyToolItem buildTool(RequestMappingInfo info, HandlerMethod handler, KeelbaseTool ann,
                                    List<String> skipped, List<String> warnings) {
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
        String where = handler.getMethod().getDeclaringClass().getSimpleName() + "#" + handler.getMethod().getName();
        if (method == null || path == null) {
            String reason = "无法解析 HTTP method/path（检查 @RequestMapping 映射是否完整）";
            log.warn("keelbase 工具跳过 {}: {}", where, reason);
            skipped.add(where + ": " + reason);
            return null;
        }
        boolean write = WRITE_METHODS.contains(method);

        String name = ann.name().isBlank() ? camelToSnake(handler.getMethod().getName()) : ann.name();
        if (name == null || !name.matches("^[a-z][a-z0-9_]{0,39}$")) {
            String reason = "工具名 '" + name + "' 非法（需 ^[a-z][a-z0-9_]{0,39}$；缺省由方法名 camelCase→snake_case）";
            log.warn("keelbase 工具跳过 {}: {}", where, reason);
            skipped.add(where + ": " + reason);
            return null;
        }
        boolean infer = ann.infer();
        Method javaMethod = handler.getMethod();
        String description;
        if (!ann.description().isBlank()) {
            description = ann.description();
        } else {
            String documented = swaggerDoc.toolDescription(javaMethod, null);
            if (documented != null && !documented.isBlank()) {
                description = documented;
            } else if (infer) {
                String javadocText = javadocDoc.toolDescription(javaMethod);
                description = javadocText.isBlank() ? autoToolDescription(javaMethod.getName()) : javadocText;
            } else {
                description = method + " " + path;
            }
        }
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
                    String pathDesc = explicitParamDescription(mp);
                    if (pathDesc == null && infer) {
                        pathDesc = javadocDoc.paramDescription(javaMethod, mp.getParameterName());
                    }
                    if (pathDesc == null || pathDesc.isBlank()) {
                        pathDesc = autoParamDescription(pname, mp.getParameterType());
                    }
                    parameters.add(new ToolParameter(pname, TypeMapper.map(mp.getParameterType()), pathDesc, true));
                }
            } else if (rp != null) {
                String pname = sanitizeParamName(shortName(rp.name(), mp));
                if (pname == null || !seen.add(pname)) {
                    continue;
                }
                // Optional<T> is inherently optional and maps by T; otherwise required =
                // @RequestParam default-required and no explicit defaultValue.
                // Optional<T> 天然可选且按 T 映射类型；其余按 @RequestParam 默认必填且未设 defaultValue。
                Class<?> effectiveType = unwrapOptional(mp);
                boolean optional = effectiveType != mp.getParameterType();
                boolean required = !optional && rp.required()
                        && org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE
                                .equals(rp.defaultValue());
                String structural = paramDescription(effectiveType, rp.defaultValue());
                String paramDesc = explicitParamDescription(mp);
                if (paramDesc == null) {
                    String documented = infer
                            ? javadocDoc.paramDescription(javaMethod, mp.getParameterName()) : "";
                    paramDesc = documented.isBlank() ? structural
                            : (structural.isBlank() ? documented : documented + "；" + structural);
                } else if (!structural.isBlank()) {
                    paramDesc = paramDesc + "；" + structural;
                }
                if (paramDesc.isBlank()) {
                    paramDesc = autoParamDescription(pname, mp.getParameterType());
                }
                parameters.add(new ToolParameter(pname, TypeMapper.map(effectiveType), paramDesc, required));
                if (write) {
                    queryParams.add(pname);
                }
            } else if (rb != null && mp.getParameterType() != null) {
                for (RequestBodyFields.BodyField bf : RequestBodyFields.of(mp.getParameterType(), infer, javadocDoc)) {
                    String fname = sanitizeParamName(bf.name());
                    if (fname == null || !seen.add(fname)) {
                        continue;
                    }
                    parameters.add(new ToolParameter(fname, bf.type(), bf.description(), bf.required()));
                }
            } else if (!SKIPPED_PARAM_TYPES.contains(mp.getParameterType())) {
                if (infer && isSimpleProperty(mp)) {
                    // Spring MVC default resolution: an unannotated simple-type parameter is an
                    // optional request parameter (RequestParamNamedValueInfo required=false).
                    // Spring MVC 默认解析：未标注的简单类型参数是可选请求参数（required=false）。
                    String pname = sanitizeParamName(mp.getParameterName());
                    if (pname != null && seen.add(pname)) {
                        String structural = paramDescription(mp.getParameterType(),
                                org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE);
                        String paramDesc = explicitParamDescription(mp);
                        if (paramDesc == null) {
                            String documented = javadocDoc.paramDescription(javaMethod, mp.getParameterName());
                            paramDesc = documented.isBlank() ? structural
                                    : (structural.isBlank() ? documented : documented + "；" + structural);
                        } else if (!structural.isBlank()) {
                            paramDesc = paramDesc + "；" + structural;
                        }
                        if (paramDesc.isBlank()) {
                            paramDesc = autoParamDescription(pname, mp.getParameterType());
                        }
                        parameters.add(new ToolParameter(pname, TypeMapper.map(mp.getParameterType()),
                                paramDesc, false));
                        if (write) {
                            queryParams.add(pname);
                        }
                    } else if (infer) {
                        warnings.add(where + ": 参数 '" + mp.getParameterName()
                                + "' 无法推断为工具参数（无法归一化参数名）");
                    }
                } else if (infer && !isDelegationUser(mp)) {
                    // Complex objects are bound as model attributes by Spring, not tool parameters;
                    // make the omission visible instead of silently dropping it.
                    // 复合对象由 Spring 按模型属性绑定，不是工具参数；显式报出，避免静默丢失。
                    String pname = mp.getParameterName() != null
                            ? mp.getParameterName() : ("#" + mp.getParameterIndex());
                    String message = where + ": 参数 '" + pname
                            + "' 未导出为工具参数（缺 @PathVariable/@RequestParam/@RequestBody；"
                            + "复合对象按 Spring 模型属性处理）";
                    log.warn("keelbase 工具推断告警 {}", message);
                    warnings.add(message);
                }
            }
        }
        return new ProxyToolItem(name, description, method, path, parameters, queryParams,
                riskLevel, revokePath);
    }

    private static String shortName(String annName, MethodParameter mp) {
        return annName.isBlank() ? mp.getParameterName() : annName;
    }

    /**
     * Explicit springdoc {@code @Parameter(description)} or {@code null} when absent. Distinguishing
     * "absent" from "blank" lets inference insert only where the developer did not declare anything.
     *
     * <p>显式的 springdoc {@code @Parameter(description)}；缺省返回 {@code null}。区分「缺省」与
     * 「空串」，推断只补开发者未声明的位置。
     */
    private String explicitParamDescription(MethodParameter mp) {
        return swaggerDoc.paramDescription(mp, null);
    }

    /**
     * Unwraps {@code Optional<T>} to {@code T}; returns the declared type otherwise. A missing
     * generic argument (raw {@code Optional}) stays as declared and is reported as {@code string}.
     *
     * <p>把 {@code Optional<T>} 解包为 {@code T}；否则返回声明类型。缺少泛型实参（裸
     * {@code Optional}）保持声明类型，并按 {@code string} 导出。
     */
    private static Class<?> unwrapOptional(MethodParameter mp) {
        Class<?> declared = mp.getParameterType();
        if (declared != java.util.Optional.class) {
            return declared;
        }
        java.lang.reflect.Type generic = mp.getGenericParameterType();
        if (generic instanceof java.lang.reflect.ParameterizedType pt
                && pt.getActualTypeArguments().length == 1
                && pt.getActualTypeArguments()[0] instanceof Class<?> inner) {
            return inner;
        }
        return declared;
    }

    /**
     * Spring's definition of a simple property that default request-param resolution accepts.
     *
     * <p>Spring 对「默认请求参数解析可接受的简单属性」的定义。
     */
    private static boolean isSimpleProperty(MethodParameter mp) {
        return mp.getParameterType() != null && BeanUtils.isSimpleProperty(mp.getParameterType());
    }

    /**
     * {@code @DelegationUser} injection is infrastructure, never a tool parameter.
     *
     * <p>{@code @DelegationUser} 注入属于基础设施，不是工具参数。
     */
    private static boolean isDelegationUser(MethodParameter mp) {
        for (java.lang.annotation.Annotation a : mp.getParameterAnnotations()) {
            if ("cn.com.keelbase.delegation.DelegationUser".equals(a.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Readable tool description from the method name, e.g. {@code listOverdueOrders} →
     * {@code list overdue orders}. Used only when {@code infer=true} and no explicit or documented
     * description exists.
     *
     * <p>由方法名生成可读工具描述，如 {@code listOverdueOrders} → {@code list overdue orders}。
     * 仅在 {@code infer=true} 且无显式/文档描述时使用。
     */
    static String autoToolDescription(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return "";
        }
        return methodName.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase();
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

    /**
     * 参数描述：枚举类型附可选值列表；@RequestParam 显式 defaultValue 附默认值
     * （与 {@link RequestBodyFields} 的枚举描述口径一致，让 LLM 工具参数更准）。
     */
    static String paramDescription(Class<?> type, String defaultValue) {
        StringBuilder sb = new StringBuilder();
        if (type != null && type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            sb.append("可选: ").append(
                    Arrays.stream(constants).map(Object::toString).collect(Collectors.joining("/")));
        }
        if (defaultValue != null
                && !org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE.equals(defaultValue)) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append("默认: ").append(defaultValue);
        }
        return sb.toString();
    }

    /**
     * 参数描述兜底（零样板）：springdoc @Parameter 与枚举/默认值均无描述时，
     * 从参数名 camelCase 分词 + 类型自动生成（如 {@code customerId} → {@code customer ID（integer）}），
     * 让 LLM 工具参数更可读。
     */
    static String autoParamDescription(String name, Class<?> type) {
        String readable = name.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase()
                .replaceAll("\\bid\\b", "ID");
        return readable + "（" + TypeMapper.map(type) + "）";
    }
}
