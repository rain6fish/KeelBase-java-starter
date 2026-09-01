package cn.com.keelbase.autoconfigure;

// SPDX-License-Identifier: Apache-2.0

import cn.com.keelbase.export.ExportConfigResolver;
import cn.com.keelbase.export.ProxyToolExportProperties;
import cn.com.keelbase.export.ProxyToolsScanner;
import cn.com.keelbase.export.ProxyToolItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolsExportValidatorTest {

    private final ProxyToolsScanner scanner = mock(ProxyToolsScanner.class);
    private final ExportConfigResolver resolver = resolverWith("http://x", "a");

    private static ExportConfigResolver resolverWith(String baseUrl, String audience) {
        ProxyToolExportProperties p = new ProxyToolExportProperties();
        p.setBaseUrl(baseUrl);
        p.setAudience(audience);
        return new ExportConfigResolver(p, null);
    }

    @Test
    void skippedDeclarationsFailStartup() {
        when(scanner.scanWithReport()).thenReturn(new ProxyToolsScanner.ScanReport(
                List.of(), List.of("FooController#doThing: 工具名 'bad-name' 非法")));
        ToolsExportValidator validator = new ToolsExportValidator(scanner, resolver);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> validator.run(null));
        org.assertj.core.api.Assertions.assertThat(e.getMessage())
                .contains("strict", "bad-name");
    }

    @Test
    void noSkippedAllowsStartup() {
        when(scanner.scanWithReport()).thenReturn(new ProxyToolsScanner.ScanReport(
                List.of(new ProxyToolItem("list_x", "d", "GET", "/x", List.of(), List.of(), "R1", null)),
                List.of()));
        ToolsExportValidator validator = new ToolsExportValidator(scanner, resolver);
        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    void missingExportConfigFailsStartup() {
        // resolver.validate() 抛 IllegalArgumentException（缺 base-url/audience）
        ExportConfigResolver failing = mock(ExportConfigResolver.class);
        doThrow(new IllegalArgumentException("缺少 baseUrl")).when(failing).validate();
        ToolsExportValidator validator = new ToolsExportValidator(scanner, failing);
        assertThrows(IllegalArgumentException.class, () -> validator.run(null));
    }
}
