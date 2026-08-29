package cn.com.keelbase.export;

/**
 * 导出配置解析：统一处理 audience 单一来源、baseUrl 规范化与必填校验。
 *
 * <p>解决两处 audience 配置（{@code keelbase.tools.audience} / {@code keelbase.delegation.audience}）
 * 的「双 source of truth」陷阱：tools.audience 缺省回退 delegation.audience。
 * baseUrl 约定为服务器根，导出时自动去尾部斜杠（防 baseUrl + path 拼出 {@code //api}）。
 */
public final class ExportConfigResolver {

    private final ProxyToolExportProperties exportProps;

    /** keelbase.delegation.audience（可空）；tools.audience 缺省时的回退源。 */
    private final String delegationAudience;

    public ExportConfigResolver(ProxyToolExportProperties exportProps, String delegationAudience) {
        this.exportProps = exportProps;
        this.delegationAudience = delegationAudience;
    }

    /** 导出用的 audience：tools.audience 优先，缺省回退 delegation.audience。 */
    public String effectiveAudience() {
        String toolsAudience = exportProps.getAudience();
        if (toolsAudience != null && !toolsAudience.isBlank()) {
            return toolsAudience;
        }
        return delegationAudience;
    }

    /** 显式配置的 tools.audience（未配置返回 null），用于诊断告警对比。 */
    public String configuredToolsAudience() {
        String toolsAudience = exportProps.getAudience();
        return toolsAudience == null || toolsAudience.isBlank() ? null : toolsAudience;
    }

    /** baseUrl 规范化：去尾部斜杠；未配置/空白返回 null。 */
    public String normalizedBaseUrl() {
        String u = exportProps.getBaseUrl();
        if (u == null || u.isBlank()) {
            return null;
        }
        return u.replaceAll("/+$", "");
    }

    /** 导出端点开关。 */
    public boolean exportEnabled() {
        return exportProps.isExportEnabled();
    }

    /** 诊断端点开关。 */
    public boolean statusEnabled() {
        return exportProps.isStatusEnabled();
    }

    /** 校验导出必需项（base-url、audience）；缺失抛 {@link IllegalArgumentException}。 */
    public void validate() {
        StringBuilder missing = new StringBuilder();
        if (normalizedBaseUrl() == null) {
            missing.append("keelbase.tools.base-url");
        }
        if (effectiveAudience() == null) {
            if (missing.length() > 0) {
                missing.append("、");
            }
            missing.append("keelbase.tools.audience（或回退源 keelbase.delegation.audience）");
        }
        if (missing.length() > 0) {
            throw new IllegalArgumentException("代理工具导出缺少必填配置: " + missing);
        }
    }
}
