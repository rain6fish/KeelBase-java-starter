// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.crmsample;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 存量 CRM 内存存储（CrmController 与 CrmInsightsController 共享）。 */
@Component
public class CrmStore {

    final Map<Long, CrmCustomer> customers = new ConcurrentHashMap<>();
    final Map<Long, CrmOrder> orders = new ConcurrentHashMap<>();
    final Map<Long, FollowupTask> followups = new ConcurrentHashMap<>();
    final AtomicLong customerId = new AtomicLong(1);
    final AtomicLong orderId = new AtomicLong(1);
    final AtomicLong taskId = new AtomicLong(1);

    public CrmStore() {
        seed();
    }

    private void seed() {
        // 客户：一个活跃、一个逾期风险（供 AI 风险分析演示）
        long a = customerId.getAndIncrement();
        long b = customerId.getAndIncrement();
        customers.put(a, new CrmCustomer(a, "蓝湾地产", "BlueBay Real Estate", "contact@bluebay.cn", "ACTIVE"));
        customers.put(b, new CrmCustomer(b, "天穹科技", "SkyTech", "ops@skytech.cn", "RISK"));
        orders.put(orderId.getAndIncrement(), new CrmOrder(1, a, 120000, "PAID", "2026-07-01"));
        orders.put(orderId.getAndIncrement(), new CrmOrder(2, a, 56000, "OVERDUE", "2026-06-15"));
        orders.put(orderId.getAndIncrement(), new CrmOrder(3, b, 980000, "OVERDUE", "2026-05-30"));
    }
}
