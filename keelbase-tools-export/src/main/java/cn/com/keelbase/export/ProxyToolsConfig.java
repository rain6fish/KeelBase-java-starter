// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.export;

import java.util.List;

/** 导出的 ai_proxy_tools 配置（写入 KeelBase Settings key ai_proxy_tools）。 */
public record ProxyToolsConfig(String baseUrl, String audience, List<ProxyToolItem> tools) {
}
