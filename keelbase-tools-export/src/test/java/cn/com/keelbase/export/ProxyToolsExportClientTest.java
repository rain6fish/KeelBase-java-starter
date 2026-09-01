package cn.com.keelbase.export;

// SPDX-License-Identifier: Apache-2.0

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyToolsExportClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> settingsBody = new AtomicReference<>();
    private final AtomicReference<String> settingsAuth = new AtomicReference<>();

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

    private void stubExport(String json) {
        server.createContext("/keelbase/proxy-tools/export",
                ex -> respond(ex, 200, json));
    }

    private void stubAll() {
        stubExport("{\"baseUrl\":\"http://127.0.0.1:8081\",\"audience\":\"legacy-crm\","
                + "\"tools\":[{\"name\":\"list_customers\",\"riskLevel\":\"R1\"}]}");
        server.createContext("/api/v1/auth/login",
                ex -> respond(ex, 200, "{\"code\":200,\"data\":{\"accessToken\":\"admin-token\"}}"));
        server.createContext("/api/v1/settings/ai_proxy_tools", ex -> {
            settingsBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            settingsAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            respond(ex, 200, "{\"code\":200,\"data\":null}");
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

    private String url() {
        return "http://127.0.0.1:" + port;
    }

    @Test
    void exportParsesAndValidates() throws Exception {
        stubExport("{\"baseUrl\":\"http://x\",\"audience\":\"a\","
                + "\"tools\":[{\"name\":\"list_customers\",\"riskLevel\":\"R1\"},"
                + "{\"name\":\"create_followup\",\"riskLevel\":\"R3\",\"revokePath\":\"DELETE /x/{id}\"}]}");
        JsonNode cfg = new ProxyToolsExportClient().export(url());
        assertEquals(2, cfg.path("tools").size());
        assertEquals("a", cfg.path("audience").asText());
    }

    @Test
    void writeOutputWritesFile() throws Exception {
        stubExport("{\"baseUrl\":\"http://x\",\"audience\":\"a\",\"tools\":[{\"name\":\"t\",\"riskLevel\":\"R1\"}]}");
        ProxyToolsExportClient client = new ProxyToolsExportClient();
        Path out = Files.createTempFile("export", ".json");
        Files.delete(out);
        client.writeOutput(client.export(url()), out.toString());
        assertTrue(Files.readString(out).contains("t"));
        Files.deleteIfExists(out);
    }

    @Test
    void emptyToolsFails() {
        stubExport("{\"baseUrl\":\"http://x\",\"audience\":\"a\",\"tools\":[]}");
        assertThrows(IOException.class, () -> new ProxyToolsExportClient().export(url()));
    }

    @Test
    void duplicateNameFails() {
        stubExport("{\"baseUrl\":\"http://x\",\"audience\":\"a\","
                + "\"tools\":[{\"name\":\"a\",\"riskLevel\":\"R1\"},{\"name\":\"a\",\"riskLevel\":\"R1\"}]}");
        assertThrows(IOException.class, () -> new ProxyToolsExportClient().export(url()));
    }

    @Test
    void badRiskLevelFails() {
        stubExport("{\"baseUrl\":\"http://x\",\"audience\":\"a\",\"tools\":[{\"name\":\"a\",\"riskLevel\":\"R9\"}]}");
        assertThrows(IOException.class, () -> new ProxyToolsExportClient().export(url()));
    }

    @Test
    void httpErrorFails() {
        // 无 context → 404
        assertThrows(IOException.class, () -> new ProxyToolsExportClient().export(url()));
    }

    @Test
    void registerWritesSettings() throws Exception {
        stubAll();
        new ProxyToolsExportClient().register(url(), url(), "admin", "Admin@1234");
        assertTrue(settingsBody.get().contains("\"value\""));
        assertTrue(settingsBody.get().contains("\"type\":\"string\""));
        assertTrue(settingsBody.get().contains("list_customers"));
        assertEquals("Bearer admin-token", settingsAuth.get());
    }

    @Test
    void loginFailureFails() {
        server.createContext("/api/v1/auth/login", ex -> respond(ex, 401, "{\"code\":401}"));
        stubExport("{\"tools\":[{\"name\":\"a\"}]}");
        assertThrows(IOException.class,
                () -> new ProxyToolsExportClient().register(url(), url(), "admin", "x"));
    }

    @Test
    void settingsWriteFailureFails() {
        server.createContext("/api/v1/auth/login",
                ex -> respond(ex, 200, "{\"code\":200,\"data\":{\"accessToken\":\"t\"}}"));
        stubExport("{\"tools\":[{\"name\":\"a\"}]}");
        server.createContext("/api/v1/settings/ai_proxy_tools", ex -> respond(ex, 403, "{\"code\":403}"));
        assertThrows(IOException.class,
                () -> new ProxyToolsExportClient().register(url(), url(), "admin", "x"));
    }
}
