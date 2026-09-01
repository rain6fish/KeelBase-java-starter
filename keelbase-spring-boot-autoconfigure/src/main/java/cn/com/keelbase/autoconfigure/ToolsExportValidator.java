package cn.com.keelbase.autoconfigure;

// SPDX-License-Identifier: Apache-2.0

import cn.com.keelbase.export.ExportConfigResolver;
import cn.com.keelbase.export.ProxyToolsScanner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * strict 模式启动校验（{@code keelbase.tools.strict=true}）：启动时扫描一次，若
 * {@code @KeelbaseTool} 声明非法（无法解析 method/path、工具名非法）被跳过，则抛异常
 * 使应用启动失败并列出每个跳过的明细——替代默认只打 WARN 导致的「导出缺工具」运行时困惑。
 */
public class ToolsExportValidator implements ApplicationRunner {

    private final ProxyToolsScanner scanner;
    private final ExportConfigResolver resolver;

    public ToolsExportValidator(ProxyToolsScanner scanner, ExportConfigResolver resolver) {
        this.scanner = scanner;
        this.resolver = resolver;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 缺 base-url/audience 时导出配置不完整，也应在启动时暴露
        resolver.validate();
        var report = scanner.scanWithReport();
        if (!report.skipped().isEmpty()) {
            throw new IllegalStateException(
                    "keelbase.tools.strict=true：以下 @KeelbaseTool 声明被跳过，应用启动失败：\n  - "
                            + String.join("\n  - ", report.skipped()));
        }
    }
}
