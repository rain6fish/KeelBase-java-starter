package cn.com.keelbase.compensation;

import cn.com.keelbase.delegation.DelegationAuthFilter;
import cn.com.keelbase.delegation.DelegationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 补偿端点脚手架基类：消除 java-compensation-example 的样板。
 *
 * <p>流程：验委托身份（无则 401）→ 幂等检查（已撤销/不存在 → {@code idempotent:true}）
 * → 调 cancelOp（软删/补偿）→ 记审计 → {@code 200 {idempotent:false, resultId, status:"revoked"}}。
 * 返回语义严格对齐 KeelBase {@code proxy-revoker}（非 2xx 即撤销失败并透传原因）。
 *
 * <pre>{@code
 * @RestController
 * public class CompensationController extends KeelBaseCompensationSupport<Followup> {
 *     CompensationController(RevocationLedgerStore ledger, CompensationAuditSink auditSink) { super(ledger, auditSink); }
 *
 *     @DeleteMapping("/api/compensation/followups/{id}")
 *     public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest req) {
 *         return handleRevoke(req, id, repo::findById, (f, subj) -> f.setCancelled(true), "compensation.followups.revoke");
 *     }
 * }
 * }</pre>
 */
public abstract class KeelBaseCompensationSupport<T> {

    private final RevocationLedgerStore ledger;
    private final CompensationAuditSink auditSink;

    protected KeelBaseCompensationSupport(RevocationLedgerStore ledger, CompensationAuditSink auditSink) {
        this.ledger = ledger;
        this.auditSink = auditSink;
    }

    /**
     * @param request    当前请求（从中读委托身份）
     * @param resultId   AI 副作用 resultId（撤销锚点）
     * @param finder     按 resultId 查找实体；null 视为已撤销/不存在 → 幂等
     * @param cancelOp   执行软删/补偿（参数：实体 + 发起撤销的身份）
     * @param auditAction 审计动作标识
     */
    protected final ResponseEntity<?> handleRevoke(HttpServletRequest request, Long resultId,
                                                   Function<Long, T> finder,
                                                   BiConsumer<T, String> cancelOp,
                                                   String auditAction) {
        DelegationPrincipal principal = (DelegationPrincipal)
                request.getAttribute(DelegationAuthFilter.PRINCIPAL_ATTR);
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "code", "delegation.missing", "message", "缺少委托身份"));
        }
        if (resultId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "compensation.invalid_result_id", "message", "resultId 缺失"));
        }
        if (ledger.isRevoked(resultId)) {
            return ResponseEntity.ok(Map.of("idempotent", true, "resultId", resultId));
        }
        T entity = finder.apply(resultId);
        if (entity == null) {
            return ResponseEntity.ok(Map.of("idempotent", true, "resultId", resultId));
        }
        String subject = principal.identity();
        cancelOp.accept(entity, subject);
        ledger.markRevoked(resultId);
        auditSink.audit(auditAction, resultId, subject);
        return ResponseEntity.ok(Map.of("idempotent", false, "resultId", resultId, "status", "revoked"));
    }
}
