package cn.com.keelbase.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 代理工具导出配置（前缀 {@code keelbase.tools}）。
 *
 * <ul>
 *   <li>{@code keelbase.tools.base-url}：目标系统 baseUrl（部署相关，不来自注解），写入导出的
 *       {@code ai_proxy_tools} 配置；</li>
 *   <li>{@code keelbase.tools.audience}：目标系统 audience（与 {@code keelbase.delegation.audience}
 *       一致）；</li>
 *   <li>{@code keelbase.tools.export-enabled}：导出端点开关（缺省 true）。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "keelbase.tools")
public class ProxyToolExportProperties {

    private String baseUrl;

    private String audience;

    private boolean exportEnabled = true;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public boolean isExportEnabled() { return exportEnabled; }
    public void setExportEnabled(boolean exportEnabled) { this.exportEnabled = exportEnabled; }
}
