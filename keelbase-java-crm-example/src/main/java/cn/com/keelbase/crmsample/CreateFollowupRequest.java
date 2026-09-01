// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.crmsample;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 创建跟进任务请求体（对齐 external-crm spec：content 必填，dueDate 可选）。 */
public record CreateFollowupRequest(
        @JsonProperty(required = true) String content,
        String dueDate) {
}
