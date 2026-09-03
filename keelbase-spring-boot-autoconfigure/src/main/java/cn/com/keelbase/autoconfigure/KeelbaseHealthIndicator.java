package cn.com.keelbase.autoconfigure;

// SPDX-License-Identifier: Apache-2.0

import cn.com.keelbase.export.KeelbaseStatusController;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 把 {@code GET /keelbase/status} 的接入健康映射到 Spring Boot actuator {@code /health}——
 * Java 团队运维面板（{@code /actuator/health}）直接看到接入状态，无需单独 curl status。
 *
 * <p>健康判定复用 {@link KeelbaseStatusController#status()}（委托/导出/工具/审计 + buildFindings），
 * 不重写规则：healthy/degraded → {@code UP}（degraded 带 warnings）；error → {@code DOWN}（带 errors）。
 * {@code status-enabled=false}（404）→ {@code UP}（不阻断，注明 status disabled）。
 */
public class KeelbaseHealthIndicator implements HealthIndicator {

    private final KeelbaseStatusController statusController;

    public KeelbaseHealthIndicator(KeelbaseStatusController statusController) {
        this.statusController = statusController;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Health health() {
        Map<String, Object> root;
        try {
            root = statusController.status();
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Health.up().withDetail("keelbase.status", "disabled").build();
            }
            throw e;
        }
        Map<String, Object> health = (Map<String, Object>) root.getOrDefault("health", Map.of());
        String status = String.valueOf(health.getOrDefault("status", "error"));
        List<String> errors = (List<String>) root.getOrDefault("errors", List.of());
        List<String> warnings = (List<String>) root.getOrDefault("warnings", List.of());

        if ("error".equals(status)) {
            return Health.down().withDetail("errors", errors).build();
        }
        Health.Builder builder = Health.up().withDetail("status", status);
        if (!warnings.isEmpty()) {
            builder.withDetail("warnings", warnings);
        }
        return builder.build();
    }
}
