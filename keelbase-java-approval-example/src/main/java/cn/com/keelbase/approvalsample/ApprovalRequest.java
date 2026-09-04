// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.approvalsample;

/** 审批请求实体（存量 Java 审批流的记录）。decidedBy = 委托身份写回（KeelBase 携身份治理）。 */
public record ApprovalRequest(
        long id,
        String title,
        String type,    // expense | purchase | leave
        long amount,
        String reason,
        String status,  // pending | auto_approved | needs_review | rejected
        String reviewer,
        String decidedBy) {

    /** 审批决定：approve → auto_approved/needs_review；reject → rejected。落 decidedBy。 */
    public ApprovalRequest decide(boolean approve, boolean needsReview, String actor) {
        String next = !approve ? "rejected" : (needsReview ? "needs_review" : "auto_approved");
        return new ApprovalRequest(id, title, type, amount, reason, next, reviewer, actor);
    }

    /** 撤销审批决定 → 恢复待审，清 decidedBy。 */
    public ApprovalRequest revert() {
        return new ApprovalRequest(id, title, type, amount, reason, "pending", reviewer, null);
    }
}
