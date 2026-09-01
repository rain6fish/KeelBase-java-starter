// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 代理工具导出配置（前缀 {@code keelbase.tools}）。
 *
 * <ul>
 *   <li>{@code keelbase.tools.base-url}：目标系统 baseUrl（部署相关，不来自注解），写入导出的
 *       {@code ai_proxy_tools} 配置。约定为<b>服务器根</b>（如 {@code http://host:8081}），
 *       工具 path 为完整路径；导出时自动去尾部斜杠。</li>
 *   <li>{@code keelbase.tools.audience}：目标系统 audience。缺省回退
 *       {@code keelbase.delegation.audience}（单一来源，避免两处配置不一致）。</li>
 *   <li>{@code keelbase.tools.export-enabled}：导出端点开关（缺省 true，生产可关）。</li>
 *   <li>{@code keelbase.tools.status-enabled}：诊断端点开关（缺省 true，生产可关）。</li>
 *   <li>{@code keelbase.tools.strict}：启动 fail-fast（缺省 false）——为 true 时若扫描发现
 *       {@code @KeelbaseTool} 声明非法（无法解析 method/path、工具名非法）则应用启动失败并列出明细，
 *       替代默认只打 WARN 跳过导致的「导出缺工具」运行时困惑。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "keelbase.tools")
public class ProxyToolExportProperties {

    private String baseUrl;

    private String audience;

    private boolean exportEnabled = true;

    private boolean statusEnabled = true;

    private boolean strict = false;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public boolean isExportEnabled() { return exportEnabled; }
    public void setExportEnabled(boolean exportEnabled) { this.exportEnabled = exportEnabled; }
    public boolean isStatusEnabled() { return statusEnabled; }
    public void setStatusEnabled(boolean statusEnabled) { this.statusEnabled = statusEnabled; }
    public boolean isStrict() { return strict; }
    public void setStrict(boolean strict) { this.strict = strict; }
}
