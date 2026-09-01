// SPDX-License-Identifier: Apache-2.0

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
 *
 * <p>配置经 {@link ExportConfigResolver} 统一处理：audience 单一来源（tools → delegation 回退）、
 * baseUrl 去尾部斜杠、缺省必填校验（缺失返回 500 + 明确原因）。
 */
@RestController
@RequestMapping("/keelbase/proxy-tools")
public class ProxyToolsExportController {

    private final ProxyToolsScanner scanner;
    private final ExportConfigResolver resolver;

    public ProxyToolsExportController(ProxyToolsScanner scanner, ExportConfigResolver resolver) {
        this.scanner = scanner;
        this.resolver = resolver;
    }

    @GetMapping("/export")
    public ProxyToolsConfig export() {
        if (!resolver.exportEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "export disabled");
        }
        try {
            resolver.validate();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return new ProxyToolsConfig(resolver.normalizedBaseUrl(), resolver.effectiveAudience(), scanner.scan());
    }
}
