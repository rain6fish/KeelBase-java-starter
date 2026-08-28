package cn.com.keelbase.autoconfigure;

import cn.com.keelbase.export.ProxyToolExportProperties;
import cn.com.keelbase.export.ProxyToolsExportController;
import cn.com.keelbase.export.ProxyToolsScanner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 自动装配代理工具导出：扫描 {@code @KeelbaseTool} 方法并暴露
 * {@code GET /keelbase/proxy-tools/export}（导出 {@code ai_proxy_tools} 配置）。
 *
 * <p>开关：{@code keelbase.tools.enabled=false} 可关闭。
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
    ProxyToolsExportController keelBaseProxyToolsExportController(ProxyToolsScanner scanner,
                                                                  ProxyToolExportProperties properties) {
        return new ProxyToolsExportController(scanner, properties);
    }
}
