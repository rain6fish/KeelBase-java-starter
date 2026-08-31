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
#    AI reads pending approval requests (R1) → pre-audits by policy → proposes a decision (R3 confirmation)
#    → audit chain → revoke via DELETE /api/compensation/approval-decisions/{id} (idempotent + audited)
```

## Health check

`GET /keelbase/status` reports delegation/export/tools/health — same diagnostics as the other reference projects.
