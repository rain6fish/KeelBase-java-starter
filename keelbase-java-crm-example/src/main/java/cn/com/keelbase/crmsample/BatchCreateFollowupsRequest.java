package cn.com.keelbase.crmsample;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 批量创建跟进任务请求体（演示复杂 body：嵌套数组 → 工具参数 string，Agent 传 JSON 数组文本）。
 *
 * <p>items 是 {@code List<BatchItem>}（嵌套对象数组），导出为 string 参数；
 * 由 Jackson 绑定把 Agent 传入的 JSON 数组反序列化。
 */
public record BatchCreateFollowupsRequest(
        @JsonProperty(required = true) Long customerId,
        @JsonProperty(required = true) List<BatchItem> items) {

    /** 单条跟进（嵌套于 items 数组）。 */
    public record BatchItem(
            @JsonProperty(required = true) String content,
            String dueDate) {
    }
}
