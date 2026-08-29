package cn.com.keelbase.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审计上报配置（前缀 {@code keelbase.audit}）。
 *
 * <ul>
 *   <li>{@code keelbase.audit.base-url}：治理台服务根（如 {@code http://localhost:3001}）；
 *       <b>为空则上报 disabled（仅本地 SLF4J 日志）</b>，对齐主应用 GovernanceReporter。</li>
 *   <li>{@code keelbase.audit.api-key}：治理台服务身份（GOVERNANCE_API_KEY），经 {@code x-api-key} 头。</li>
 *   <li>{@code keelbase.audit.enabled}：总开关（缺省 true；base-url 空时仍整体 disabled）。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "keelbase.audit")
public class KeelbaseAuditProperties {

    /** 治理台服务根（如 http://localhost:3001）。 */
    private String baseUrl;

    /** 治理台服务身份（GOVERNANCE_API_KEY），x-api-key 头。 */
    private String apiKey;

    private boolean enabled = true;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
