// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.crmsample;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 修改订单金额请求体（对齐 external-crm spec：amount 必填）。 */
public record UpdateAmountRequest(
        @JsonProperty(required = true) double amount) {
}
