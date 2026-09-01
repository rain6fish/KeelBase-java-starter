package cn.com.keelbase.export;

// SPDX-License-Identifier: Apache-2.0

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 导出/注册 {@code ai_proxy_tools} 的 HTTP 客户端（纯 JDK {@link HttpClient} + Jackson，
 * 不依赖 Maven/Gradle/Spring 容器）——被 Maven 插件（keelbase-maven-plugin）、Gradle task
 * 模板（gradle/keelbase.gradle）与 {@link KeelbaseToolsCli} 复用。
 *
 * <p>导出复用运行中应用的 {@code GET /keelbase/proxy-tools/export} 端点（运行时扫描器）；
 * 注册 = admin 登录 → 导出 → {@code PUT /api/v1/settings/ai_proxy_tools}（配合 KeelBase 热更新免重启）。
 */
public class ProxyToolsExportClient {

    public static final String DEFAULT_APP_URL = "http://localhost:8081";
    public static final String DEFAULT_KEELBASE_URL = "http://localhost:3000";
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "Admin@1234";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Consumer<String> log;

    public ProxyToolsExportClient() {
        this(System.out::println);
    }

    public ProxyToolsExportClient(Consumer<String> log) {
        this.log = log;
    }

    /** GET {appUrl}/keelbase/proxy-tools/export → 解析并校验，返回配置 JSON。 */
    public JsonNode export(String appUrl) throws IOException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(appUrl) + "/keelbase/proxy-tools/export"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> res = send(req);
        if (res.statusCode() >= 300) {
            throw new IOException("导出失败: GET /keelbase/proxy-tools/export -> HTTP "
                    + res.statusCode() + ": " + res.body());
        }
        JsonNode cfg = mapper.readTree(res.body());
        validate(cfg);
        return cfg;
    }

    /** 写导出 JSON 到文件（pretty，父目录自动建）。 */
    public void writeOutput(JsonNode cfg, String output) throws IOException {
        Path p = Path.of(output);
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        Files.writeString(p, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cfg),
                StandardCharsets.UTF_8);
        log.accept("ai_proxy_tools 配置已写入: " + p.toAbsolutePath());
    }

    /** 导出 + admin 登录 + 写入 KeelBase Settings（热更新生效，免重启）。 */
    public void register(String appUrl, String keelbaseUrl, String username, String password)
            throws IOException {
        JsonNode cfg = export(appUrl);
        String token = login(keelbaseUrl, username, password);
        String body = mapper.createObjectNode()
                .put("value", cfg.toString())
                .put("type", "string")
                .toString();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(keelbaseUrl) + "/api/v1/settings/ai_proxy_tools"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = send(req);
        if (res.statusCode() >= 300) {
            throw new IOException("写入 ai_proxy_tools 失败: HTTP " + res.statusCode() + ": " + res.body());
        }
        log.accept("ai_proxy_tools 已写入 KeelBase（" + cfg.path("tools").size()
                + " 个工具，audience=" + cfg.path("audience").asText() + "）");
        log.accept("KeelBase 已热更新，无需重启——下次 AI 对话即可用新工具");
    }

    private String login(String keelbaseUrl, String username, String password) throws IOException {
        String body = mapper.createObjectNode()
                .put("username", username)
                .put("password", password)
                .toString();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(keelbaseUrl) + "/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = send(req);
        if (res.statusCode() >= 300) {
            throw new IOException("登录失败: HTTP " + res.statusCode() + ": " + res.body());
        }
        String token = mapper.readTree(res.body()).path("data").path("accessToken").asText("");
        if (token.isEmpty()) {
            throw new IOException("登录响应缺少 accessToken");
        }
        return token;
    }

    /** 校验：tools 非空、name 唯一、riskLevel 合法（R0-R5）。 */
    private void validate(JsonNode cfg) throws IOException {
        JsonNode tools = cfg.path("tools");
        if (!tools.isArray() || tools.isEmpty()) {
            throw new IOException("导出配置缺少 tools（空工具集）——确认应用已用 @KeelbaseTool 声明工具");
        }
        Set<String> names = new HashSet<>();
        for (JsonNode t : tools) {
            String name = t.path("name").asText("");
            if (!names.add(name)) {
                throw new IOException("工具名重复: " + name);
            }
            String risk = t.path("riskLevel").asText("");
            if (!risk.isEmpty() && !Set.of("R0", "R1", "R2", "R3", "R4", "R5").contains(risk)) {
                throw new IOException("非法 riskLevel: " + risk + "（工具 " + name + "）");
            }
        }
    }

    private HttpResponse<String> send(HttpRequest req) throws IOException {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断: " + e.getMessage(), e);
        }
    }

    public static String trimSlash(String url) {
        return url.replaceAll("/+$", "");
    }
}
