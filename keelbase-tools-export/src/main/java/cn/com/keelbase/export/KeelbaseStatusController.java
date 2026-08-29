package cn.com.keelbase.export;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 诊断端点 {@code GET /keelbase/status}：报告委托配置、导出配置、工具计数与接入告警，
 * 帮助 Java 团队在接入 KeelBase 时快速定位配置问题。
 *
 * <p>安全：只报 {@code secretConfigured} 布尔，<b>绝不回显密钥明文</b>；也不含任何业务数据。
 * 开关：{@code keelbase.tools.status-enabled}（缺省 true，生产可关）。
 */
@RestController
@RequestMapping("/keelbase")
public class KeelbaseStatusController {

    private final ProxyToolsScanner scanner;
    private final ExportConfigResolver resolver;
    private final DelegationSnapshot delegation;

    public KeelbaseStatusController(ProxyToolsScanner scanner, ExportConfigResolver resolver,
                                    DelegationSnapshot delegation) {
        this.scanner = scanner;
        this.resolver = resolver;
        this.delegation = delegation;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        if (!resolver.statusEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "status disabled");
        }
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> delegationInfo = new LinkedHashMap<>();
        delegationInfo.put("configured", delegation.configured());
        delegationInfo.put("secretConfigured", delegation.secretConfigured());
        delegationInfo.put("audience", delegation.audience());
        delegationInfo.put("issuer", delegation.issuer());
        delegationInfo.put("protectedPaths", delegation.protectedPaths());
        root.put("delegation", delegationInfo);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("enabled", resolver.exportEnabled());
        export.put("baseUrl", resolver.normalizedBaseUrl());
        export.put("audience", resolver.effectiveAudience());
        root.put("export", export);

        List<ProxyToolItem> tools = resolver.exportEnabled() ? scanner.scan() : List.of();
        Map<String, Object> toolsInfo = new LinkedHashMap<>();
        toolsInfo.put("count", tools.size());
        toolsInfo.put("names", tools.stream().map(ProxyToolItem::name).collect(Collectors.toList()));
        root.put("tools", toolsInfo);

        root.put("warnings", buildWarnings(tools));
        return root;
    }

    private List<String> buildWarnings(List<ProxyToolItem> tools) {
        List<String> warnings = new ArrayList<>();
        String toolsAudience = resolver.configuredToolsAudience();
        String delegationAudience = delegation.audience();
        if (toolsAudience != null && delegationAudience != null && !toolsAudience.equals(delegationAudience)) {
            warnings.add("keelbase.tools.audience (" + toolsAudience
                    + ") 与 keelbase.delegation.audience (" + delegationAudience
                    + ") 不一致，会导致 KeelBase 转发请求委托验签失败");
        }
        if (resolver.effectiveAudience() == null) {
            warnings.add("audience 未配置（keelbase.tools.audience 与 keelbase.delegation.audience 均为空）");
        }
        if (resolver.normalizedBaseUrl() == null) {
            warnings.add("keelbase.tools.base-url 未配置，导出配置无法被 KeelBase 使用");
        }
        if (resolver.exportEnabled() && tools.isEmpty()) {
            warnings.add("未发现 @KeelbaseTool 工具（检查注解是否已加在 @RestController 方法上）");
        }
        return warnings;
    }
}
