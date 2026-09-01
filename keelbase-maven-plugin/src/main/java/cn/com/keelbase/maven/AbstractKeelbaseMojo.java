package cn.com.keelbase.maven;

// SPDX-License-Identifier: Apache-2.0

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

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

/**
 * 导出/注册 {@code ai_proxy_tools} 的公共逻辑（B 路径，AI Bridge §4）：调用运行中应用的
 * {@code GET /keelbase/proxy-tools/export} → 校验 → 写文件/写入 KeelBase Settings。
 *
 * <p>导出依赖运行中的应用（复用运行时扫描器），因此插件目标是已启动的 Java Starter 应用地址。
 */
public abstract class AbstractKeelbaseMojo extends AbstractMojo {

    /** 运行中的应用地址（服务器根，如 http://localhost:8081） */
    @Parameter(property = "keelbase.appUrl", defaultValue = "http://localhost:8081")
    protected String appUrl;

    /** 导出 JSON 写入路径（ExportMojo） */
    @Parameter(property = "keelbase.output", defaultValue = "target/ai_proxy_tools.json")
    protected String output;

    protected final ObjectMapper mapper = new ObjectMapper();
    protected final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** GET {appUrl}/keelbase/proxy-tools/export → 解析 JSON 配置。 */
    protected JsonNode fetchExport() throws MojoExecutionException {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(appUrl) + "/keelbase/proxy-tools/export"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                throw new MojoExecutionException(
                        "导出失败: GET /keelbase/proxy-tools/export -> HTTP "
                                + res.statusCode() + ": " + res.body());
            }
            return mapper.readTree(res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("导出被中断", e);
        } catch (IOException e) {
            throw new MojoExecutionException("导出失败: " + e.getMessage(), e);
        }
    }

    /** 校验：tools 非空、name 唯一、riskLevel 合法（R0-R5）。 */
    protected void validate(JsonNode cfg) throws MojoExecutionException {
        JsonNode tools = cfg.path("tools");
        if (!tools.isArray() || tools.isEmpty()) {
            throw new MojoExecutionException(
                    "导出配置缺少 tools（空工具集）——确认应用已用 @KeelbaseTool 声明工具");
        }
        Set<String> names = new HashSet<>();
        for (JsonNode t : tools) {
            String name = t.path("name").asText("");
            if (!names.add(name)) {
                throw new MojoExecutionException("工具名重复: " + name);
            }
            String risk = t.path("riskLevel").asText("");
            if (!risk.isEmpty() && !Set.of("R0", "R1", "R2", "R3", "R4", "R5").contains(risk)) {
                throw new MojoExecutionException("非法 riskLevel: " + risk + "（工具 " + name + "）");
            }
        }
    }

    /** 写导出 JSON 到 {@code output}。 */
    protected void writeOutput(JsonNode cfg) throws MojoExecutionException {
        try {
            Path p = Path.of(output);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cfg),
                    StandardCharsets.UTF_8);
            getLog().info("ai_proxy_tools 配置已写入: " + p.toAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException("写文件失败: " + output, e);
        }
    }

    protected static String trimSlash(String url) {
        return url.replaceAll("/+$", "");
    }
}
