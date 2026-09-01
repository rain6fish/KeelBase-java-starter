// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.approvalsample;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 内存存储 + 种子数据（模拟存量 Java 审批流的请求库）。 */
@Component
public class ApprovalStore {

    private final Map<Long, ApprovalRequest> requests = new LinkedHashMap<>();

    public ApprovalStore() {
        // 对齐 AI Approval 旗舰：小额（¥800 ≤ 阈值自动通过）与大额（¥12000 转人工）请求
        requests.put(1L, new ApprovalRequest(1L, "8 月差旅报销", "expense", 800,
                "客户拜访交通与住宿费", "pending", null));
        requests.put(2L, new ApprovalRequest(2L, "研发服务器采购", "purchase", 12000,
                "双节点生产环境扩容", "pending", null));
        requests.put(3L, new ApprovalRequest(3L, "团队年会预算", "expense", 5000,
                "Q4 团建活动", "needs_review", "alex"));
    }

    public List<ApprovalRequest> list() {
        return new ArrayList<>(requests.values());
    }

    public ApprovalRequest get(long id) {
        return requests.get(id);
    }

    public ApprovalRequest replace(ApprovalRequest req) {
        requests.put(req.id(), req);
        return req;
    }
}
