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

1. AI asks "which projects are at risk?" → `query_projects` + `get_project` run automatically (R1) over real project data.
2. AI proposes creating a task → KeelBase raises a **confirmation request** (R3) → a human approves.
3. The write lands in `create_pm_task` → recorded in the audit chain as a revocable side effect.
4. `DELETE /api/compensation/pm-tasks/{id}` revokes it (idempotent, delegated identity, audited).

## Health check

`GET /keelbase/status` reports delegation/export/tools/health — see [reference-project-crm.md](reference-project-crm.md) for the same diagnostics.
