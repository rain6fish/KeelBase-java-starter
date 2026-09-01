package cn.com.keelbase.export;

// SPDX-License-Identifier: Apache-2.0

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * 命令行入口：导出/注册 {@code ai_proxy_tools}，供 Gradle task 模板
 * （{@code gradle/keelbase.gradle} 的 JavaExec task）调用。
 *
 * <pre>
 *   java -cp ... cn.com.keelbase.export.KeelbaseToolsCli export   [--appUrl ...] [--output ...]
 *   java -cp ... cn.com.keelbase.export.KeelbaseToolsCli register [--appUrl ...] [--keelbaseUrl ...] [--username ...] [--password ...]
 * </pre>
 */
public final class KeelbaseToolsCli {

    private KeelbaseToolsCli() {
    }

    public static void main(String[] args) {
        String cmd = args.length > 0 ? args[0] : "export";
        ProxyToolsExportClient client = new ProxyToolsExportClient(System.out::println);
        try {
            switch (cmd) {
                case "export" -> {
                    String appUrl = arg(args, "--appUrl", ProxyToolsExportClient.DEFAULT_APP_URL);
                    String output = arg(args, "--output", "target/ai_proxy_tools.json");
                    JsonNode cfg = client.export(appUrl);
                    client.writeOutput(cfg, output);
                    System.out.println("已导出 " + cfg.path("tools").size()
                            + " 个工具（audience=" + cfg.path("audience").asText() + "）");
                    System.out.println("下一步：./gradlew keelbaseRegister 或 mvn keelbase:register 写入 KeelBase");
                }
                case "register" -> {
                    String appUrl = arg(args, "--appUrl", ProxyToolsExportClient.DEFAULT_APP_URL);
                    String keelbaseUrl = arg(args, "--keelbaseUrl", ProxyToolsExportClient.DEFAULT_KEELBASE_URL);
                    String username = arg(args, "--username", ProxyToolsExportClient.DEFAULT_USERNAME);
                    String password = arg(args, "--password", ProxyToolsExportClient.DEFAULT_PASSWORD);
                    client.register(appUrl, keelbaseUrl, username, password);
                }
                default -> {
                    System.err.println("未知子命令: " + cmd);
                    usage();
                    System.exit(2);
                }
            }
        } catch (IOException e) {
            System.err.println("FAIL: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String arg(String[] args, String key, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static void usage() {
        System.err.println("用法: KeelbaseToolsCli export|register [--appUrl http://localhost:8081] "
                + "[--keelbaseUrl http://localhost:3000] [--username admin] [--password ...] [--output target/ai_proxy_tools.json]");
    }
}
