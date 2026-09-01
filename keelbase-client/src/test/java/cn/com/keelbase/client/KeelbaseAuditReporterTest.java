// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KeelbaseAuditReporter：异步上报 /external/audit + x-api-key 头 + 字段透传；未配置本地回退。 */
class KeelbaseAuditReporterTest {

    private HttpServer server;
    private final CountDownLatch received = new CountDownLatch(1);
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/external/audit", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
            received.countDown();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private KeelbaseAuditReporter reporter(String baseUrl, String key) {
        KeelbaseAuditProperties props = new KeelbaseAuditProperties();
        props.setBaseUrl(baseUrl);
        props.setApiKey(key);
        return new KeelbaseAuditReporter(props);
    }

    @Test
    void report_sendsToExternalAudit_withApiKeyHeader_andFields() throws Exception {
        KeelbaseAuditEvent event = KeelbaseAuditEvent.builder()
                .userId("42").username("alex").action("compensation.followups.revoke")
                .detail("revoke followup 1").source("java").durationMs(12L).isError(false).build();

        reporter("http://localhost:" + server.getAddress().getPort(), "gov-key-123").report(event);

        assertTrue(received.await(3, TimeUnit.SECONDS), "异步上报应到达 stub");
        assertTrue("gov-key-123".equals(apiKey.get()), "应携带 x-api-key 服务身份");
        assertTrue(body.get().contains("\"action\":\"compensation.followups.revoke\""));
        assertTrue(body.get().contains("\"userId\":\"42\""));
        assertTrue(body.get().contains("\"username\":\"alex\""));
        assertTrue(body.get().contains("\"source\":\"java\""));
        assertTrue(body.get().contains("\"durationMs\":12"));
    }

    @Test
    void report_disabledWithoutBaseUrl_noThrow() {
        KeelbaseAuditReporter reporter = reporter(null, "key");
        assertFalse(reporter.isEnabled());
        reporter.report(KeelbaseAuditEvent.builder().action("x").build()); // 本地日志，不抛
    }
}
