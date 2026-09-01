package cn.com.keelbase.maven;

// SPDX-License-Identifier: Apache-2.0

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;

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
        try {
            client.register(appUrl, keelbaseUrl, username, password);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
