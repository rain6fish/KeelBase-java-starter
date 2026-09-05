// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * KeelbaseClient 配置（前缀 {@code keelbase.client}）。
 *
 * <ul>
 *   <li>{@code keelbase.client.base-url}：KeelBase 服务根（如 {@code http://localhost:3000}），
 *       用于调 {@code /api/v1/auth/delegation-token}。未配置时仅本地验签可用。</li>
 *   <li>{@code keelbase.client.audience}：目标系统 audience；缺省回退
 *       {@code keelbase.delegation.audience}。</li>
 *   <li>{@code keelbase.client.connect-timeout} / {@code read-timeout}：HTTP 超时。</li>
 *   <li>{@code keelbase.client.side-effect-api-key}：查询副作用状态用的服务身份
 *       （x-api-key，需为 KeelBase 主应用接受的 {@code GOVERNANCE_API_KEY}）；未配置时
 *       {@link KeelbaseClient#querySideEffect} 抛清晰配置错误。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "keelbase.client")
public class KeelbaseClientProperties {

    /** KeelBase 服务根（如 http://localhost:3000）。 */
    private String baseUrl;

    /** 目标系统 audience（缺省回退 keelbase.delegation.audience）。 */
    private String audience;

    /** 查询副作用状态的服务身份（x-api-key，= KeelBase 主应用 GOVERNANCE_API_KEY）。 */
    private String sideEffectApiKey;

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(10);

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getSideEffectApiKey() { return sideEffectApiKey; }
    public void setSideEffectApiKey(String sideEffectApiKey) { this.sideEffectApiKey = sideEffectApiKey; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
