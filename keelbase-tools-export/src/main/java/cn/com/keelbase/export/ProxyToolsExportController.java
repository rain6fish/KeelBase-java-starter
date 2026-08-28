package cn.com.keelbase.export;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 导出 {@code ai_proxy_tools} 配置：把 {@code @KeelbaseTool} 声明的工具输出为可直接
 * 写入 KeelBase Settings（key {@code ai_proxy_tools}）的 JSON。
 *
 * <p>接入步骤：{@code GET /keelbase/proxy-tools/export} → 结果作为
 * {@code {"value":"<json 字符串>","type":"string"}} PUT {@code /settings/ai_proxy_tools}
 * → 重启 KeelBase 生效。
 */
@RestController
@RequestMapping("/keelbase/proxy-tools")
public class ProxyToolsExportController {

    private final ProxyToolsScanner scanner;
    private final ProxyToolExportProperties properties;

    public ProxyToolsExportController(ProxyToolsScanner scanner, ProxyToolExportProperties properties) {
        this.scanner = scanner;
        this.properties = properties;
    }

    @GetMapping("/export")
    public ProxyToolsConfig export() {
        if (!properties.isExportEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "export disabled");
        }
        return new ProxyToolsConfig(properties.getBaseUrl(), properties.getAudience(), scanner.scan());
    }
}
