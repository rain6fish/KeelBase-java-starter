# Reference Project — Legacy Java Approval Flow → AI Approval (real Java implementation)

The **Integrator Kit Reference Project (Approval)**: a legacy Java approval system wired to KeelBase as governed AI tools. The real Java side of the AI Approval flagship (AI pre-audit + human review) — reads auto-executed (R1), approval decisions need human confirmation (R3) and are revocable via the compensation endpoint. Same governance loop as the [CRM](reference-project-crm.md) and [PM](reference-project-pm.md) reference projects.

> 集成商参考项目（审批）：传统 Java 审批流 → AI Approval（Java 侧真实实现）。对齐 KeelBase AI Approval 旗舰（AI 预审 + 人工复核）——读 R1 自动 / 审批决定 R3 确认 + 补偿撤销，与 [CRM](reference-project-crm.zh-CN.md)/[PM](reference-project-pm.zh-CN.md) 参考项目同一治理闭环。

## The domain

| Tool | Endpoint | Risk | Governance |
|---|---|---|---|
| `query_approval_requests` | `GET /api/approval-requests` | R1 | auto-executed (read) |
| `get_approval_request` | `GET /api/approval-requests/{id}` | R1 | auto-executed (read) |
| `decide_approval_request` | `PATCH /api/approval-requests/{id}/decision` | **R3** | **human confirmation** + revocable |

Seed data mirrors the AI Approval flagship: a ¥800 expense request (below threshold → auto-pass) and a ¥12000 purchase (→ human review).

## Run it

```bash
cd keelbase-java-approval-example
mvn spring-boot:run          # http://localhost:8084
```

## Wire into KeelBase

```bash
# 1. Export the ai_proxy_tools config (3 approval tools)
curl http://localhost:8084/keelbase/proxy-tools/export

# 2. PUT the exported JSON into KeelBase Settings (value = the JSON string), restart KeelBase
#    (PUT /api/v1/settings/ai_proxy_tools, audience = legacy-approval)

# 3. Full loop (KeelBase + this module running):
node scripts/verify-approval-e2e.mjs --configure   # export + write config (then restart KeelBase)
node scripts/verify-approval-e2e.mjs --verify      # confirmation gate → approve → write back → audit → revoke → compensation
```

## The governed AI loop

```
You: "Pre-audit the pending approval requests."

AI:
  1. query_approval_requests (R1, auto) → find the ¥800 expense + ¥12000 purchase
  2. pre-audit by policy → propose decide_approval_request (approve)
  3. confirmation gate (R3) — not executed without your approval
  4. approve → proxy writes the decision back to the Java approval flow with delegated identity (decidedBy = your user)
  5. audit hash chain records it; revoke → compensation endpoint restores the request to pending (idempotent)
```

## Delegated identity write-back

`decide_approval_request` takes `@DelegationUser DelegationPrincipal` and stores `decidedBy = principal.identity()` (your `oidcSub`, or `local:<id>` after stripping the prefix; defaults to `anonymous` when no delegation token is present). KeelBase's "identity-carrying governance" therefore reaches into the legacy Java system — the decision knows *who* (via the AI) approved it, and revoking the decision clears `decidedBy` back to `pending`. Verified by `ApprovalCompensationTest` (decide → assert `decidedBy` + status → revoke → idempotent → restored to `pending` → 401 fail-closed).

## Same domain, two integration paths

| | B-path OpenAPI proxy | This Java implementation |
|---|---|---|
| Declaration | `specs/external-approval.openapi.json` → `keelbase init --import-openapi-proxy` | `@KeelbaseTool` on real endpoints |
| Tool set | identical 3 approval tools | identical 3 approval tools |
| Write gate | R3 (same) | R3 (same) |
| Revoke | revokePath → Java compensation | `KeelBaseCompensationSupport` (idempotent + audit) |

The two are drop-in interchangeable from KeelBase's perspective — same AI tools, only the declaration surface differs (annotation on real Java vs. OpenAPI import).

## Health check

`GET /keelbase/status` reports delegation/export/tools/health — same diagnostics as the other reference projects.

## Configuration

See [configuration](configuration.md) — `keelbase.delegation.*` (shared `DELEGATION_SECRET`, `audience: legacy-approval`) and `keelbase.tools.base-url` (server root `http://localhost:8084`). The compensation path `/api/compensation` is fail-closed (no anonymous revocation).
