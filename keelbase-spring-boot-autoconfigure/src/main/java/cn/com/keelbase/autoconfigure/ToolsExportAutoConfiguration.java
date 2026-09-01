// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.autoconfigure;

import cn.com.keelbase.client.KeelbaseAuditProperties;
import cn.com.keelbase.delegation.DelegationProperties;
import cn.com.keelbase.export.DelegationSnapshot;
import cn.com.keelbase.export.ExportConfigResolver;
import cn.com.keelbase.export.KeelbaseStatusController;
import cn.com.keelbase.export.ProxyToolExportProperties;
import cn.com.keelbase.export.ProxyToolsExportController;
import cn.com.keelbase.export.ProxyToolsScanner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 自动装配代理工具导出：
 * <ul>
 *   <li>{@link ProxyToolsScanner} 扫描 {@code @KeelbaseTool} 方法；</li>
 *   <li>{@link ExportConfigResolver} 统一 audience 单一来源（tools → delegation 回退）与 baseUrl 规范化；</li>
 *   <li>{@code GET /keelbase/proxy-tools/export} 导出 {@code ai_proxy_tools} 配置；</li>
 *   <li>{@code GET /keelbase/status} 诊断端点（委托/导出/工具 + 告警，不泄露密钥）。</li>
 * </ul>
 *
 * <p>开关：{@code keelbase.tools.enabled=false} 整体关闭；导出/诊断可分别用
 * {@code export-enabled}/{@code status-enabled} 控制。委托配置经 {@link ObjectProvider}
 * 可选注入——委托能力关闭时导出/诊断仍可用（回退源为 null，告警提示）。
 */
@AutoConfiguration
@EnableConfigurationProperties(ProxyToolExportProperties.class)
@ConditionalOnClass({RequestMappingHandlerMapping.class, ProxyToolsScanner.class})
@ConditionalOnProperty(prefix = "keelbase.tools", name = "enabled", matchIfMissing = true)
public class ToolsExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ProxyToolsScanner keelBaseProxyToolsScanner(RequestMappingHandlerMapping mapping) {
        return new ProxyToolsScanner(mapping);
    }

    @Bean
    @ConditionalOnMissingBean
    ExportConfigResolver keelBaseExportConfigResolver(ProxyToolExportProperties properties,
                                                      ObjectProvider<DelegationProperties> delegation) {
        DelegationProperties d = delegation.getIfAvailable();
        return new ExportConfigResolver(properties, d == null ? null : d.getAudience());
    }

    @Bean
    @ConditionalOnMissingBean
    ProxyToolsExportController keelBaseProxyToolsExportController(ProxyToolsScanner scanner,
                                                                  ExportConfigResolver resolver) {
        return new ProxyToolsExportController(scanner, resolver);
    }

    @Bean
    @ConditionalOnMissingBean
    KeelbaseStatusController keelbaseStatusController(ProxyToolsScanner scanner,
                                                      ExportConfigResolver resolver,
                                                      ObjectProvider<DelegationProperties> delegation,
                                                      KeelbaseAuditProperties audit) {
        DelegationProperties d = delegation.getIfAvailable();
        DelegationSnapshot snapshot = d == null ? DelegationSnapshot.unconfigured()
                : new DelegationSnapshot(true, isSet(d.getSecret()), d.getAudience(), d.getIssuer(),
                        d.getPaths() == null ? java.util.List.of() : d.getPaths());
        return new KeelbaseStatusController(scanner, resolver, snapshot, audit);
    }

    @Bean
    @ConditionalOnProperty(prefix = "keelbase.tools", name = "strict", havingValue = "true")
    ToolsExportValidator keelBaseToolsExportValidator(ProxyToolsScanner scanner,
                                                      ExportConfigResolver resolver) {
        return new ToolsExportValidator(scanner, resolver);
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }
}
