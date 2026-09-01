package cn.com.keelbase.maven;

// SPDX-License-Identifier: Apache-2.0

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterMojoTest {

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

    private void stubAll() {
        server.createContext("/api/v1/auth/login",
                ex -> respond(ex, 200, "{\"code\":200,\"data\":{\"accessToken\":\"admin-token\"}}"));
        server.createContext("/keelbase/proxy-tools/export",
                ex -> respond(ex, 200, "{\"baseUrl\":\"http://127.0.0.1:8081\",\"audience\":\"legacy-crm\","
                        + "\"tools\":[{\"name\":\"list_customers\",\"riskLevel\":\"R1\"}]}"));
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

    private RegisterMojo mojo() {
        RegisterMojo mojo = new RegisterMojo();
        mojo.appUrl = "http://127.0.0.1:" + port;
        mojo.keelbaseUrl = "http://127.0.0.1:" + port;
        mojo.username = "admin";
        mojo.password = "Admin@1234";
        return mojo;
    }

    @Test
    void registerWritesSettings() throws Exception {
        stubAll();
        mojo().execute();
        // PUT body：{value: <导出JSON字符串>, type: "string"}
        assertTrue(settingsBody.get().contains("\"value\""));
        assertTrue(settingsBody.get().contains("\"type\":\"string\""));
        assertTrue(settingsBody.get().contains("list_customers"));
        assertEquals("Bearer admin-token", settingsAuth.get());
    }

    @Test
    void loginFailureFails() {
        server.createContext("/api/v1/auth/login",
                ex -> respond(ex, 401, "{\"code\":401,\"message\":\"bad credentials\"}"));
        server.createContext("/keelbase/proxy-tools/export",
                ex -> respond(ex, 200, "{\"tools\":[{\"name\":\"a\"}]}"));
        assertThrows(MojoExecutionException.class, () -> mojo().execute());
    }

    @Test
    void settingsWriteFailureFails() {
        server.createContext("/api/v1/auth/login",
                ex -> respond(ex, 200, "{\"code\":200,\"data\":{\"accessToken\":\"admin-token\"}}"));
        server.createContext("/keelbase/proxy-tools/export",
                ex -> respond(ex, 200, "{\"tools\":[{\"name\":\"a\"}]}"));
        server.createContext("/api/v1/settings/ai_proxy_tools",
                ex -> respond(ex, 403, "{\"code\":403,\"message\":\"forbidden\"}"));
        assertThrows(MojoExecutionException.class, () -> mojo().execute());
    }
}
