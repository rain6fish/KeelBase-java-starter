package cn.com.keelbase.client;

// SPDX-License-Identifier: Apache-2.0

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeelbasePolicyClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> authHeader = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void stubPolicy(int status, String body) {
        server.createContext("/api/v1/external/governance/policy", ex -> {
            authHeader.set(ex.getRequestHeaders().getFirst("x-api-key"));
            respond(ex, status, body);
        });
    }

    private static void respond(HttpExchange ex, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private KeelbasePolicyClient client() {
        KeelbaseAuditProperties props = new KeelbaseAuditProperties();
        props.setBaseUrl("http://127.0.0.1:" + port);
        props.setApiKey("governance-key");
        return new KeelbasePolicyClient(props);
    }

    @Test
    void fetchReturnsParsedPolicy() {
        stubPolicy(200, "{\"code\":200,\"data\":{\"tools\":{\"create_followup\":{\"requiresConfirmation\":true},\"query_customers\":{\"enabled\":false,\"allowedRoles\":[\"admin\"]}},\"audit\":{\"granularity\":\"write\"},\"updatedAt\":\"2026-09-03T00:00:00Z\"}}");
        Optional<GovernancePolicy> opt = client().fetch();
        assertTrue(opt.isPresent());
        GovernancePolicy p = opt.get();
        assertTrue(p.tools().get("create_followup").requiresConfirmation());
        assertFalse(p.tools().get("query_customers").enabled());
        assertEquals("write", p.auditGranularity());
        // 服务身份 x-api-key 送达
        assertEquals("governance-key", authHeader.get());
    }

    @Test
    void fetchEmptyPolicyDefaults() {
        stubPolicy(200, "{\"code\":200,\"data\":{}}");
        GovernancePolicy p = client().fetch().orElseThrow();
        assertTrue(p.tools().isEmpty());
        assertEquals("all", p.auditGranularity());
    }

    @Test
    void fetchHttpErrorThrows() {
        stubPolicy(401, "{\"code\":401,\"message\":\"unauthorized\"}");
        assertThrows(KeelbaseClientException.class, () -> client().fetch());
    }

    @Test
    void disabledReturnsEmpty() {
        KeelbaseAuditProperties props = new KeelbaseAuditProperties();
        // base-url 空 → disabled
        KeelbasePolicyClient client = new KeelbasePolicyClient(props);
        assertFalse(client.isEnabled());
        assertEquals(Optional.empty(), client.fetch());
    }
}
