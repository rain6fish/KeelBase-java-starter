// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.approvalsample;

import cn.com.keelbase.annotation.KeelbaseTool;
import cn.com.keelbase.compensation.CompensationAuditSink;
import cn.com.keelbase.compensation.KeelBaseCompensationSupport;
import cn.com.keelbase.compensation.RevocationLedgerStore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 存量 Java 审批流的受治理 AI 工具：
 * 查询审批请求（读 R1 自动）+ 审批决定（写 R3 需确认，可撤销补偿）。
 *
 * <p>对齐 KeelBase AI Approval 旗舰（AI 预审 + 人工复核）——AI 读待审批请求，
 * 审批决定（批准/拒绝）需人工确认，撤销走补偿端点（幂等 + 审计 + 委托身份）。
 */
@RestController
@RequestMapping("/api")
public class ApprovalController extends KeelBaseCompensationSupport<ApprovalRequest> {

    private final ApprovalStore store;

    public ApprovalController(RevocationLedgerStore ledger, CompensationAuditSink auditSink, ApprovalStore store) {
        super(ledger, auditSink);
        this.store = store;
    }

    @GetMapping("/approval-requests")
    @KeelbaseTool(name = "query_approval_requests", description = "审批请求列表（读工具，R1 自动）——类型/金额/状态/申请人，AI 预审分级依据")
    public List<ApprovalRequest> list() {
        return store.list();
    }

    @GetMapping("/approval-requests/{id}")
    @KeelbaseTool(name = "get_approval_request", description = "审批请求详情（读工具，R1 自动）")
    public ResponseEntity<?> get(@PathVariable long id) {
        ApprovalRequest req = store.get(id);
        return req == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(req);
    }

    @PatchMapping("/approval-requests/{id}/decision")
    @KeelbaseTool(name = "decide_approval_request",
            description = "审批决定（写工具，R3 需人工确认；撤销走补偿端点）——approve/reject，autoApproved=小额自动通过/needsReview=转人工",
            revokePath = "DELETE /api/compensation/approval-decisions/{id}")
    public ResponseEntity<?> decide(@PathVariable long id, @RequestBody DecisionRequest req) {
        ApprovalRequest current = store.get(id);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }
        ApprovalRequest decided = current.decide(req.approve(), req.needsReview());
        return ResponseEntity.ok(store.replace(decided));
    }

    /** 补偿端点：KeelBase 撤销 AI 的审批决定时调用（恢复待审，幂等 + 审计 + 委托身份）。 */
    @DeleteMapping("/compensation/approval-decisions/{id}")
    public ResponseEntity<?> revoke(@PathVariable long id, HttpServletRequest request) {
        return handleRevoke(request, id, store::get,
                (req, subject) -> store.replace(req.revert()),
                "compensation.approval-decision.revoke");
    }

    /** 审批决定请求（写工具 body，字段对齐 KeelBase 生成器 DTO）。 */
    public record DecisionRequest(
            @Schema(description = "是否批准", required = true) boolean approve,
            @Schema(description = "是否转人工复核（approve 时）") boolean needsReview) {
    }
}
