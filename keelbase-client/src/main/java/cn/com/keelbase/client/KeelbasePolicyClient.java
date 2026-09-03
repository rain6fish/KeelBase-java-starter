package cn.com.keelbase.client;

// SPDX-License-Identifier: Apache-2.0

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * 从治理台拉取实时治理策略（D2-3 {@code GET /api/v1/external/governance/policy}，
 * 服务身份 {@code x-api-key}）——Java 业务系统本地按治理策略约束执行（工具开关 / 确认 / 角色白名单 / 审计粒度）。
 *
 * <p>复用 {@link KeelbaseAuditProperties}（{@code keelbase.audit.base-url} + {@code api-key} = 治理台地址 + 服务身份）；
 * 未配置 → disabled，{@code fetch()} 返回 {@code Optional.empty()}。
 */
public class KeelbasePolicyClient {

    private static final String EXTERNAL_POLICY_PATH = "/api/v1/external/governance/policy";

    private final KeelbaseAuditProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public KeelbasePolicyClient(KeelbaseAuditProperties properties) {
        this.properties = properties;
        this.mapper = new ObjectMapper();
        this.http = HttpClient.newBuilder().build();
    }

    /** base-url + api-key 已配置才可拉取（对齐治理台上报语义）。 */
    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
                && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    /**
     * 拉取实时治理策略（同步读）。
     *
     * @return 策略；未配置 base-url/api-key → {@code Optional.empty()}
     * @throws KeelbaseClientException HTTP ≥300 / 响应解析失败
     */
    public Optional<GovernancePolicy> fetch() {
        if (!isEnabled()) {
            return Optional.empty();
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(properties.getBaseUrl()) + EXTERNAL_POLICY_PATH))
                .header("x-api-key", properties.getApiKey())
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new KeelbaseClientException(
                        "拉取治理策略 HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isObject()) {
                throw new KeelbaseClientException("拉取治理策略响应缺少 data: " + response.body());
            }
            return Optional.of(GovernancePolicy.from(data));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeelbaseClientException("拉取治理策略被中断", e);
        } catch (IOException e) {
            throw new KeelbaseClientException("拉取治理策略失败: " + e.getMessage(), e);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.replaceAll("/+$", "");
    }
}
