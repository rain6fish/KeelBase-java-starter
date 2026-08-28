package cn.com.keelbase.export;

import java.util.List;

/**
 * 单个代理工具配置（对齐 KeelBase ai_proxy_tools.tools 条目）。
 *
 * @param queryParams 写方法中 @RequestParam 参数名列表（ProxyTool 拼 query string，其余进 body）
 * @param revokePath  Java 端补偿端点路径（撤销时 KeelBase 调用），可空
 */
public record ProxyToolItem(
        String name,
        String description,
        String method,
        String path,
        List<ToolParameter> parameters,
        List<String> queryParams,
        String riskLevel,
        String revokePath) {
}
