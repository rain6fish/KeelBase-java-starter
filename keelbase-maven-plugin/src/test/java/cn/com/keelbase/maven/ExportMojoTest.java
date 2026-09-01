package cn.com.keelbase.maven;

// SPDX-License-Identifier: Apache-2.0

import com.sun.net.httpserver.HttpServer;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportMojoTest {

    private HttpServer server;
    private int port;

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
        server.createContext("/keelbase/proxy-tools/export", exchange -> {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
    }

    private ExportMojo mojo(Path out) {
        ExportMojo mojo = new ExportMojo();
        mojo.appUrl = "http://127.0.0.1:" + port;
        mojo.output = out.toString();
        return mojo;
    }

    private static final String VALID = "{\"baseUrl\":\"http://127.0.0.1:8081\",\"audience\":\"legacy-crm\","
            + "\"tools\":[{\"name\":\"list_customers\",\"description\":\"列出客户\",\"method\":\"GET\","
            + "\"path\":\"/api/customers\",\"parameters\":[],\"riskLevel\":\"R1\"},"
            + "{\"name\":\"create_followup\",\"description\":\"创建跟进\",\"method\":\"POST\","
            + "\"path\":\"/api/followups\",\"parameters\":[],\"riskLevel\":\"R3\"}]}";

    @Test
    void exportWritesFile() throws Exception {
        stubExport(VALID);
        Path out = Files.createTempFile("export", ".json");
        Files.delete(out);
        mojo(out).execute();
        String content = Files.readString(out);
        assertTrue(content.contains("list_customers"));
        assertTrue(content.contains("create_followup"));
        assertTrue(content.contains("legacy-crm"));
        Files.deleteIfExists(out);
    }

    @Test
    void emptyToolsFails() {
        stubExport("{\"baseUrl\":\"http://x\",\"audience\":\"a\",\"tools\":[]}");
        assertThrows(MojoExecutionException.class, () -> mojo(Path.of("target/test-export-empty.json")).execute());
    }

    @Test
    void duplicateNameFails() {
        stubExport("{\"baseUrl\":\"http://x\",\"audience\":\"a\",\"tools\":[{\"name\":\"a\",\"riskLevel\":\"R1\"},{\"name\":\"a\",\"riskLevel\":\"R1\"}]}");
        assertThrows(MojoExecutionException.class, () -> mojo(Path.of("target/test-export-dup.json")).execute());
    }

    @Test
    void badRiskLevelFails() {
        stubExport("{\"baseUrl\":\"http://x\",\"audience\":\"a\",\"tools\":[{\"name\":\"a\",\"riskLevel\":\"R9\"}]}");
        assertThrows(MojoExecutionException.class, () -> mojo(Path.of("target/test-export-risk.json")).execute());
    }

    @Test
    void httpErrorFails() {
        // 无 context → 404
        assertThrows(MojoExecutionException.class, () -> mojo(Path.of("target/test-export-404.json")).execute());
    }
}
