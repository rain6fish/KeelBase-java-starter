package cn.com.keelbase.autoconfigure;

// SPDX-License-Identifier: Apache-2.0

import cn.com.keelbase.export.KeelbaseStatusController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeelbaseHealthIndicatorTest {

    private static Map<String, Object> statusRoot(String healthStatus, List<String> warnings, List<String> errors) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("health", Map.of("status", healthStatus));
        root.put("warnings", warnings);
        root.put("errors", errors);
        return root;
    }

    private KeelbaseStatusController mockController(Map<String, Object> root) {
        KeelbaseStatusController ctrl = mock(KeelbaseStatusController.class);
        when(ctrl.status()).thenReturn(root);
        return ctrl;
    }

    @Test
    void healthyMapsUp() {
        Health h = new KeelbaseHealthIndicator(
                mockController(statusRoot("healthy", List.of(), List.of()))).health();
        assertEquals(Status.UP, h.getStatus());
    }

    @Test
    void degradedMapsUpWithWarnings() {
        Health h = new KeelbaseHealthIndicator(mockController(
                statusRoot("degraded", List.of("audit 未配置"), List.of()))).health();
        assertEquals(Status.UP, h.getStatus());
        assertEquals("degraded", h.getDetails().get("status"));
        assertTrue(h.getDetails().containsKey("warnings"));
    }

    @Test
    void errorMapsDownWithErrors() {
        Health h = new KeelbaseHealthIndicator(mockController(
                statusRoot("error", List.of(), List.of("audience 未配置")))).health();
        assertEquals(Status.DOWN, h.getStatus());
        assertTrue(h.getDetails().containsKey("errors"));
    }

    @Test
    void statusDisabledMapsUpNotBlocking() {
        KeelbaseStatusController ctrl = mock(KeelbaseStatusController.class);
        when(ctrl.status()).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        Health h = new KeelbaseHealthIndicator(ctrl).health();
        assertEquals(Status.UP, h.getStatus());
    }
}
