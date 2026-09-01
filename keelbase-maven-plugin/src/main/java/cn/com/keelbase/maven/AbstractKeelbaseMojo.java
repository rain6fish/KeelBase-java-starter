package cn.com.keelbase.maven;

// SPDX-License-Identifier: Apache-2.0

import cn.com.keelbase.export.ProxyToolsExportClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;

/**
 * 导出/注册 {@code ai_proxy_tools} 的 Mojo 公共参数与委托逻辑——HTTP/JSON 全部下沉到
 * {@link ProxyToolsExportClient}（纯 JDK，Maven 插件 / Gradle task / CLI 复用），本类只做
 * Maven 参数映射 + 日志 + 异常转换。
 */
public abstract class AbstractKeelbaseMojo extends AbstractMojo {

    /** 运行中的应用地址（服务器根，如 http://localhost:8081） */
    @Parameter(property = "keelbase.appUrl", defaultValue = "http://localhost:8081")
    protected String appUrl;

    /** 导出 JSON 写入路径（ExportMojo） */
    @Parameter(property = "keelbase.output", defaultValue = "target/ai_proxy_tools.json")
    protected String output;

    protected final ProxyToolsExportClient client =
            new ProxyToolsExportClient((msg) -> {
                if (getLog() != null) {
                    getLog().info(msg);
                } else {
                    System.out.println(msg);
                }
            });

    /** GET {appUrl}/keelbase/proxy-tools/export（含校验），Maven 异常包装。 */
    protected JsonNode fetchExport() throws MojoExecutionException {
        try {
            return client.export(appUrl);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /** 写导出 JSON 到 {@code output}。 */
    protected void writeOutput(JsonNode cfg) throws MojoExecutionException {
        try {
            client.writeOutput(cfg, output);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
