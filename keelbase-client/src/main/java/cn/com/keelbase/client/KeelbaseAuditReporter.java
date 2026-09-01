// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Java 侧审计上报：把业务动作作为审计事件写入治理台统一审计链（D2-3a {@code /external/audit}）。
 *
 * <p>配置 {@code keelbase.audit.base-url} + {@code api-key} 后启用（对齐主应用
 * {@code GovernanceReporter} 的「未配置完全本地」语义）；未配置 → disabled，仅本地 SLF4J 日志。
 * 上报异步、失败静默（warn 不抛），不阻塞业务主流程。
 */
public class KeelbaseAuditReporter {

    private static final Logger log = LoggerFactory.getLogger(KeelbaseAuditReporter.class);

    private static final String EXTERNAL_AUDIT_PATH = "/api/v1/external/audit";

    private final KeelbaseAuditProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public KeelbaseAuditReporter(KeelbaseAuditProperties properties) {
        this.properties = properties;
        this.mapper = new ObjectMapper();
        this.http = HttpClient.newBuilder().build();
    }

    /** base-url 已配置且 enabled 时才真正上报。 */
    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank();
    }

    /** 异步上报审计事件；未配置 base-url → 本地日志；失败仅 warn。 */
    public void report(KeelbaseAuditEvent event) {
        if (!isEnabled()) {
            log.info("[keelbase-audit] local (keelbase.audit.base-url 未配置): action={} userId={} detail={}",
                    event.action(), event.userId(), event.detail());
            return;
        }
        CompletableFuture.runAsync(() -> send(event))
                .exceptionally(ex -> {
                    log.warn("[keelbase-audit] 上报失败（静默）: {}", ex.getMessage());
                    return null;
                });
    }

    private void send(KeelbaseAuditEvent event) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(properties.getBaseUrl()) + EXTERNAL_AUDIT_PATH))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", properties.getApiKey() == null ? "" : properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(event), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("[keelbase-audit] 上报 HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("[keelbase-audit] 上报异常: {}", e.getMessage());
        }
    }

    private String toJson(KeelbaseAuditEvent e) throws JsonProcessingException {
        ObjectNode node = mapper.createObjectNode();
        node.put("userId", e.userId() == null ? "" : e.userId());
        if (e.username() != null) node.put("username", e.username());
        node.put("action", e.action());
        if (e.detail() != null) node.put("detail", e.detail());
        if (e.model() != null) node.put("model", e.model());
        if (e.provider() != null) node.put("provider", e.provider());
        if (e.agentId() != null) node.put("agentId", e.agentId());
        node.put("source", e.source() == null ? "java" : e.source());
        if (e.promptTokens() != null) node.put("promptTokens", e.promptTokens());
        if (e.completionTokens() != null) node.put("completionTokens", e.completionTokens());
        if (e.durationMs() != null) node.put("durationMs", e.durationMs());
        node.put("isError", e.isError());
        if (e.errorMessage() != null) node.put("errorMessage", e.errorMessage());
        return mapper.writeValueAsString(node);
    }

    private static String trimTrailingSlash(String url) {
        return url.replaceAll("/+$", "");
    }
}
