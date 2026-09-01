package cn.com.keelbase.maven;

// SPDX-License-Identifier: Apache-2.0

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@code mvn keelbase:register} — 一键把运行中应用的 {@code ai_proxy_tools} 导出并写入 KeelBase
 * Settings（admin 登录 → 导出 → PUT）。配合 KeelBase 热更新，写配置即生效，无需重启。
 */
@Mojo(name = "register", threadSafe = true)
public class RegisterMojo extends AbstractKeelbaseMojo {

    @Parameter(property = "keelbase.keelbaseUrl", defaultValue = "http://localhost:3000")
    protected String keelbaseUrl;

    @Parameter(property = "keelbase.username", defaultValue = "admin")
    protected String username;

    @Parameter(property = "keelbase.password", defaultValue = "Admin@1234")
    protected String password;

    @Override
    public void execute() throws MojoExecutionException {
        JsonNode cfg = fetchExport();
        validate(cfg);
        String token = login();
        String body = mapper.createObjectNode()
                .put("value", cfg.toString())
                .put("type", "string")
                .toString();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(keelbaseUrl) + "/api/v1/settings/ai_proxy_tools"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                throw new MojoExecutionException(
                        "写入 ai_proxy_tools 失败: HTTP " + res.statusCode() + ": " + res.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("写入被中断", e);
        } catch (IOException e) {
            throw new MojoExecutionException("写入失败: " + e.getMessage(), e);
        }
        getLog().info("ai_proxy_tools 已写入 KeelBase（" + cfg.path("tools").size()
                + " 个工具，audience=" + cfg.path("audience").asText() + "）");
        getLog().info("KeelBase 已热更新，无需重启——下次 AI 对话即可用新工具");
    }

    private String login() throws MojoExecutionException {
        String body = mapper.createObjectNode()
                .put("username", username)
                .put("password", password)
                .toString();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(keelbaseUrl) + "/api/v1/auth/login"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                throw new MojoExecutionException("登录失败: HTTP " + res.statusCode() + ": " + res.body());
            }
            JsonNode root = mapper.readTree(res.body());
            String token = root.path("data").path("accessToken").asText("");
            if (token.isEmpty()) {
                throw new MojoExecutionException("登录响应缺少 accessToken: " + res.body());
            }
            return token;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("登录被中断", e);
        } catch (IOException e) {
            throw new MojoExecutionException("登录失败: " + e.getMessage(), e);
        }
    }
}
