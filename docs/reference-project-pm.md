# Reference Project — Legacy Java PM → AI Project (real Java implementation)

The **Integrator Kit Reference Project (PM)**: a legacy Java project-management system wired to KeelBase as governed AI tools. The real Java side of the AI Project flagship (deadline-risk analysis) — the same governance loop as the [CRM reference project](reference-project-crm.md): reads auto-executed (R1), writes need human confirmation (R3) and are revocable via the compensation endpoint.

> 集成商参考项目（PM）：传统 Java 项目管理 → AI Project（Java 侧真实实现）。对齐 KeelBase AI Project 旗舰（项目延期风险分析）——读 R1 自动 / 写 R3 确认 + 补偿撤销，与 [CRM 参考项目](reference-project-crm.zh-CN.md) 同一治理闭环。

## The domain

| Tool | Endpoint | Risk | Governance |
|---|---|---|---|
| `query_projects` | `GET /api/projects` | R1 | auto-executed (read) |
| `get_project` | `GET /api/projects/{id}` | R1 | auto-executed (read, incl. tasks) |
| `create_pm_task` | `POST /api/projects/{id}/tasks` | **R3** | **human confirmation** + revocable |

## Run it

```bash
cd keelbase-java-pm-example
mvn spring-boot:run          # http://localhost:8083
```

Seed data: three projects (one `active/high` — e.g. the e-commerce refactor with overdue milestones — enough for the AI deadline-risk demo) + tasks.

## Wire into KeelBase

```bash
# 1. Export the ai_proxy_tools config (3 PM tools)
curl http://localhost:8083/keelbase/proxy-tools/export

# 2. PUT the exported JSON into KeelBase Settings (value = the JSON string), restart KeelBase
#    (PUT /api/v1/settings/ai_proxy_tools, audience = legacy-pm)

# 3. Full loop (KeelBase + this module running):
#    AI reads projects/tasks (R1) → assesses deadline risk → creates a task (R3 confirmation)
#    → audit chain → revoke via DELETE /api/compensation/pm-tasks/{id} (idempotent + audited)
```

## The governed AI loop

```
You: "Which projects are at risk of delay?"

AI:
  1. query_projects (R1, auto) → find the active/high project 电商平台重构
  2. get_project (R1, auto) → read its tasks/milestones
  3. deadline-risk analysis → propose create_pm_task
  4. confirmation gate (R3) — not executed without your approval
  5. approve → proxy writes back to the Java PM with delegated identity (createdBy = your user)
  6. audit hash chain records it; revoke → compensation endpoint soft-cancels the task (idempotent)
```

## Delegated identity write-back

`create_pm_task` takes `@DelegationUser DelegationPrincipal` and stores `createdBy = principal.identity()` (your `oidcSub`, or `local:<id>` after stripping the prefix; defaults to `anonymous` when no delegation token is present). KeelBase's "identity-carrying governance" therefore reaches into the legacy Java system — the record knows *who* (via the AI) created it, and that identity survives revocation (a cancelled task keeps its `createdBy`). Verified by `PmCompensationTest` (write → assert `createdBy` → revoke → idempotent → 401 fail-closed).

## Same domain, two integration paths

| | B-path OpenAPI proxy | This Java implementation |
|---|---|---|
| Declaration | `specs/external-pm.openapi.json` → `keelbase init --import-openapi-proxy` | `@KeelbaseTool` on real endpoints |
| Tool set | identical 3 PM tools | identical 3 PM tools |
| Write gate | R3 (same) | R3 (same) |
| Revoke | revokePath → Java compensation | `KeelBaseCompensationSupport` (idempotent + audit) |

The two are drop-in interchangeable from KeelBase's perspective — same AI tools, only the declaration surface differs (annotation on real Java vs. OpenAPI import).

## Health check

`GET /keelbase/status` reports delegation/export/tools/health — see [reference-project-crm.md](reference-project-crm.md) for the same diagnostics.

## Configuration

See [configuration](configuration.md) — `keelbase.delegation.*` (shared `DELEGATION_SECRET`, `audience: legacy-pm`) and `keelbase.tools.base-url` (server root `http://localhost:8083`). The compensation path `/api/compensation` is fail-closed (no anonymous revocation).
