package cn.com.keelbase.export;

import cn.com.keelbase.client.KeelbaseAuditProperties;
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
 * 诊断端点 {@code GET /keelbase/status}：报告委托配置、导出配置、工具计数、审计上报与接入告警，
 * 帮助 Java 团队在接入 KeelBase 时快速定位配置问题。
 *
 * <p>安全：只报 {@code secretConfigured}/{@code auditConfigured} 布尔，<b>绝不回显密钥明文</b>；
 * 也不含任何业务数据。开关：{@code keelbase.tools.status-enabled}（缺省 true，生产可关）。
 */
@RestController
@RequestMapping("/keelbase")
public class KeelbaseStatusController {

    private final ProxyToolsScanner scanner;
    private final ExportConfigResolver resolver;
    private final DelegationSnapshot delegation;
    private final KeelbaseAuditProperties audit;

    public KeelbaseStatusController(ProxyToolsScanner scanner, ExportConfigResolver resolver,
                                    DelegationSnapshot delegation, KeelbaseAuditProperties audit) {
        this.scanner = scanner;
        this.resolver = resolver;
        this.delegation = delegation;
        this.audit = audit;
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

        // 审计上报状态（keelbase.audit.*）：configured = 总开关开 且 base-url 已配置（不泄 api-key）
        boolean auditConfigured = audit.isEnabled()
                && audit.getBaseUrl() != null && !audit.getBaseUrl().isBlank();
        Map<String, Object> auditInfo = new LinkedHashMap<>();
        auditInfo.put("enabled", audit.isEnabled());
        auditInfo.put("configured", auditConfigured);
        auditInfo.put("baseUrl", audit.getBaseUrl());
        root.put("audit", auditInfo);

        List<ProxyToolItem> tools = resolver.exportEnabled() ? scanner.scan() : List.of();
        Map<String, Object> toolsInfo = new LinkedHashMap<>();
        toolsInfo.put("count", tools.size());
        toolsInfo.put("names", tools.stream().map(ProxyToolItem::name).collect(Collectors.toList()));
        toolsInfo.put("riskDistribution", tools.stream()
                .collect(Collectors.groupingBy(ProxyToolItem::riskLevel, Collectors.counting())));
        long revokeCovered = tools.stream()
                .filter(t -> t.revokePath() != null && !t.revokePath().isBlank()).count();
        toolsInfo.put("revokeCovered", revokeCovered);
        root.put("tools", toolsInfo);

        // 接入健康度：阻断性 ERROR 与配置性 WARN 分级，overall health
        List<Finding> findings = buildFindings(tools, revokeCovered, auditConfigured);
        List<String> errors = findings.stream().filter(f -> f.severity() == Severity.ERROR)
                .map(Finding::message).collect(Collectors.toList());
        List<String> warnings = findings.stream().filter(f -> f.severity() == Severity.WARN)
                .map(Finding::message).collect(Collectors.toList());
        root.put("warnings", warnings);
        root.put("errors", errors);

        String healthStatus = errors.isEmpty() ? (warnings.isEmpty() ? "healthy" : "degraded") : "error";
        String summary = errors.isEmpty()
                ? (warnings.isEmpty() ? "接入配置完整，工具可正常导出" : "有配置提示，不影响基本使用")
                : "存在阻断问题（" + errors.size() + " 项），修复前 KeelBase 无法正确调用工具";
        root.put("health", Map.of("status", healthStatus, "summary", summary));
        return root;
    }

    private enum Severity { ERROR, WARN }

    private record Finding(Severity severity, String message) {}

    private List<Finding> buildFindings(List<ProxyToolItem> tools, long revokeCovered, boolean auditConfigured) {
        List<Finding> findings = new ArrayList<>();
        String toolsAudience = resolver.configuredToolsAudience();
        String delegationAudience = delegation.audience();
        if (toolsAudience != null && delegationAudience != null && !toolsAudience.equals(delegationAudience)) {
            findings.add(new Finding(Severity.ERROR, "keelbase.tools.audience (" + toolsAudience
                    + ") 与 keelbase.delegation.audience (" + delegationAudience
                    + ") 不一致，会导致 KeelBase 转发请求委托验签失败"));
        }
        if (resolver.exportEnabled()) {
            if (resolver.effectiveAudience() == null) {
                findings.add(new Finding(Severity.ERROR,
                        "audience 未配置（keelbase.tools.audience 与 keelbase.delegation.audience 均为空）"));
            }
            if (resolver.normalizedBaseUrl() == null) {
                findings.add(new Finding(Severity.ERROR,
                        "keelbase.tools.base-url 未配置，导出配置无法被 KeelBase 使用"));
            }
            if (tools.isEmpty()) {
                findings.add(new Finding(Severity.ERROR,
                        "未发现 @KeelbaseTool 工具（检查注解是否已加在 @RestController 方法上）"));
            }
        } else {
            findings.add(new Finding(Severity.WARN,
                    "keelbase.tools.export-enabled=false，工具导出已关闭——健康不代表工具可导出"));
        }
        long writeTools = tools.stream()
                .filter(t -> List.of("POST", "PUT", "PATCH", "DELETE").contains(t.method())).count();
        if (writeTools > 0 && revokeCovered == 0) {
            findings.add(new Finding(Severity.WARN,
                    "存在 " + writeTools + " 个写工具但均未配置 revokePath，AI 写副作用将无法撤销（诚实语义：无本地撤销）"));
        }
        if (!auditConfigured) {
            findings.add(new Finding(Severity.WARN,
                    "keelbase.audit 未配置（base-url 为空），业务动作不会上报治理台审计（仅本地日志回退）"));
        }
        return findings;
    }
}
