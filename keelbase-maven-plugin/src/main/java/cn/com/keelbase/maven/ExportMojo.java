package cn.com.keelbase.maven;

// SPDX-License-Identifier: Apache-2.0

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * {@code mvn keelbase:export} — 从运行中的应用导出 {@code ai_proxy_tools} 配置到 JSON 文件。
 *
 * <p>替代「curl export + 复制 JSON」：产物可直接 {@code mvn keelbase:register} 写入 KeelBase，
 * 或手动 {@code PUT /api/v1/settings/ai_proxy_tools}（写配置即热更新，无需重启 KeelBase）。
 */
@Mojo(name = "export", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ExportMojo extends AbstractKeelbaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        JsonNode cfg = fetchExport();
        validate(cfg);
        writeOutput(cfg);
        getLog().info("已导出 " + cfg.path("tools").size() + " 个工具"
                + "（audience=" + cfg.path("audience").asText() + "）");
        getLog().info("下一步：mvn keelbase:register 写入 KeelBase，或手动 PUT /settings/ai_proxy_tools");
    }
}
