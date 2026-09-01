// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.example;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 创建跟进任务的请求体（@KeelbaseTool 扫描器据此推断 body 字段参数）。 */
public record FollowupRequest(
        @JsonProperty(required = true) String content,
        Long customerId,
        Priority priority) {

    public enum Priority { LOW, MEDIUM, HIGH }
}
