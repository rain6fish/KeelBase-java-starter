// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.crmsample;

/**
 * 存量 CRM 订单。
 *
 * @param status PAID / OVERDUE（逾期——AI 风险分析的关键信号）
 */
public record CrmOrder(long id, long customerId, double amount, String status, String dueDate) {
}
