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
 * </ul>
 */
@ConfigurationProperties(prefix = "keelbase.client")
public class KeelbaseClientProperties {

    /** KeelBase 服务根（如 http://localhost:3000）。 */
    private String baseUrl;

    /** 目标系统 audience（缺省回退 keelbase.delegation.audience）。 */
    private String audience;

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(10);

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
