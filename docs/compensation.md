# Compensation & Revocation

When the AI (in KeelBase) writes to your Java system and that side effect later has to be undone, KeelBase calls back into **your** compensation endpoint. The starter's `KeelBaseCompensationSupport` scaffold removes the boilerplate: delegated-identity check, idempotency, and audit — you only supply the "how to cancel" logic.

## 1. The call contract

KeelBase's `proxy-revoker` calls your compensation endpoint with the delegated identity attached:

```
DELETE {baseUrl}{revokePath}{resultId}
headers: Authorization: Bearer <delegation JWT>
```

- `revokePath` comes from the tool's `@KeelbaseTool(revokePath = "DELETE /api/compensation/followups/{id}")` — format is `METHOD path` with the `{id}` placeholder.
- `{id}` is filled with the side effect's `resultId`.
- Your endpoint must return **2xx** to signal success and be **idempotent** (repeat calls return the same result without error).

## 2. The scaffold

Extend `KeelBaseCompensationSupport<T>` and implement one endpoint:

```java
@RestController
@RequestMapping("/api")
public class FollowupController extends KeelBaseCompensationSupport<Map<String, Object>> {

    public FollowupController(RevocationLedgerStore ledger, CompensationAuditSink auditSink) {
        super(ledger, auditSink);
    }

    @DeleteMapping("/compensation/followups/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
        return handleRevoke(request, id,
                store::get,                                   // finder: resultId → entity (null = already gone)
                (item, subject) -> item.put("cancelled", true), // cancelOp: soft-delete / compensate
                "compensation.followups.revoke");             // audit action id
    }
}
```

`handleRevoke` runs, in order:

1. **Identity** — a delegated principal must be present (else 401 `delegation.missing`).
2. **Idempotency** — if `resultId` was already revoked or `finder` returns null, respond `200 {idempotent:true, resultId}` without touching data.
3. **Cancel** — invoke `cancelOp(entity, subject)`.
4. **Record** — mark the id revoked and emit an audit event.
5. **Respond** — `200 {idempotent:false, resultId, status:"revoked"}`.

## 3. Response semantics

| Response | Meaning |
|---|---|
| 2xx | Revocation accepted. `idempotent:false` = first time; `idempotent:true` = already revoked / unknown id. |
| non-2xx | KeelBase treats revocation as failed and surfaces the reason — so a hard-delete that throws, or a lock conflict, is visible to the operator. |

## 4. Idempotency ledger

`RevocationLedger` is the default `RevocationLedgerStore`: an in-memory LRU (cap `keelbase.compensation.ledger-size`, default 1024). It is correct for a **single instance**.

For **multi-instance / HA**, implement `RevocationLedgerStore` backed by your database and register it as a bean — it replaces the default:

```java
@Bean
RevocationLedgerStore revocationLedger(JdbcTemplate jdbc) {
    return new JdbcRevocationLedger(jdbc); // your implementation
}
```

The interface is minimal: `boolean markRevoked(long resultId)` and `boolean isRevoked(long resultId)`. Prefer your existing row state as the source of truth and use the ledger only as a fast-path guard.

## 5. Audit

The default `CompensationAuditSink` logs to SLF4J. To record into your audit table, implement the interface and register a bean:

```java
@Bean
CompensationAuditSink auditSink(AuditRepository audits) {
    return (action, resultId, subject) ->
        audits.save(new CompensationAudit(action, resultId, subject, Instant.now()));
}
```

The signature matches the delegation identity, so "who revoked what and why" stays traceable end-to-end with KeelBase's audit hash chain.

## 6. Soft vs hard delete

Prefer **soft delete** (a `cancelled` flag, as in the examples): it keeps the row for audit and lets `finder` return null only when the row is truly gone. If you must hard-delete, do it inside a transaction and let exceptions propagate (non-2xx → operator-visible failure).

## 7. Where revocation is triggered in KeelBase

- **Admin console** → AI 工具与副作用 → revoke button (admin).
- **工作台 / AI 执行轨迹** → 本人撤销 (the acting user).
- Only tools that declared a `revokePath` support external revocation; others return an honest "no local compensation endpoint" message.
