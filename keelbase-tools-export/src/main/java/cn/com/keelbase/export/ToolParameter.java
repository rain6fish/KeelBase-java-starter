package cn.com.keelbase.export;

/** 单个工具参数（对齐 KeelBase ProxyTool parameters 条目）。 */
public record ToolParameter(String name, String type, String description, boolean required) {
}
