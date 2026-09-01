// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 导出配置解析：audience 单一来源回退 / baseUrl 规范化 / 必填校验。 */
class ExportConfigResolverTest {

    private ProxyToolExportProperties props() {
        return new ProxyToolExportProperties();
    }

    @Test
    void audience_fallsBack_toDelegationAudience() {
        ExportConfigResolver r = new ExportConfigResolver(props(), "legacy-crm");
        assertEquals("legacy-crm", r.effectiveAudience());
    }

    @Test
    void audience_explicitToolsAudience_wins() {
        ProxyToolExportProperties p = props();
        p.setAudience("erp-system");
        ExportConfigResolver r = new ExportConfigResolver(p, "legacy-crm");
        assertEquals("erp-system", r.effectiveAudience());
    }

    @Test
    void audience_bothMissing_null() {
        ExportConfigResolver r = new ExportConfigResolver(props(), null);
        assertNull(r.effectiveAudience());
    }

    @Test
    void baseUrl_trailingSlashes_stripped() {
        ProxyToolExportProperties p = props();
        p.setBaseUrl("http://localhost:8081///");
        ExportConfigResolver r = new ExportConfigResolver(p, "legacy-crm");
        assertEquals("http://localhost:8081", r.normalizedBaseUrl());
    }

    @Test
    void baseUrl_blank_null() {
        ProxyToolExportProperties p = props();
        p.setBaseUrl("  ");
        ExportConfigResolver r = new ExportConfigResolver(p, "legacy-crm");
        assertNull(r.normalizedBaseUrl());
    }

    @Test
    void validate_ok_whenBaseUrlAndAudiencePresent() {
        ProxyToolExportProperties p = props();
        p.setBaseUrl("http://localhost:8081");
        ExportConfigResolver r = new ExportConfigResolver(p, "legacy-crm");
        r.validate();
    }

    @Test
    void validate_missingBaseUrl_throwsWithMessage() {
        ExportConfigResolver r = new ExportConfigResolver(props(), "legacy-crm");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, r::validate);
        assertEquals("代理工具导出缺少必填配置: keelbase.tools.base-url", e.getMessage());
    }

    @Test
    void validate_missingAudience_throwsWithMessage() {
        ProxyToolExportProperties p = props();
        p.setBaseUrl("http://localhost:8081");
        ExportConfigResolver r = new ExportConfigResolver(p, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, r::validate);
        assertEquals("代理工具导出缺少必填配置: keelbase.tools.audience（或回退源 keelbase.delegation.audience）",
                e.getMessage());
    }
}
