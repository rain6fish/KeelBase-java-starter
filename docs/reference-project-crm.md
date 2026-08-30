# Reference Project — Legacy Java CRM → AI CRM (real Java implementation)

The **Integrator Kit Reference Project**: a legacy Java CRM wired to KeelBase as governed AI tools. This is the **real Java side** of the CRM domain that `external-crm-demo.md` (KeelBase main repo, B-path OpenAPI proxy) describes — same five tools, same risk levels, same governance loop. An integrator follows the [8-step manual](../../docs/integrator-kit/reference-project-guide.md) and uses this module as the "legacy system" to attach.

> 集成商参考项目：传统 Java CRM → AI CRM（Java 侧真实实现）。与 `external-crm-demo.md`（B 路径 OpenAPI 代理）描述同一 CRM 域——5 个工具、风险级、治理闭环完全对齐。

## The domain

| Tool | Endpoint | Risk | Governance |
|---|---|---|---|
| `list_customers` | `GET /api/customers?keyword=` | R1 | auto-executed (read) |
| `get_customer` | `GET /api/customers/{id}` | R1 | auto-executed (read) |
| `list_customer_orders` | `GET /api/customers/{id}/orders` | R1 | auto-executed (read) |
| `create_followup_task` | `POST /api/customers/{id}/followups` | **R3** | **human confirmation** + revocable |
| `update_order_amount` | `PATCH /api/customers/{id}/orders/{orderId}` | **R3** | **human confirmation** (price changes are irreversible → honest revocation) |

## Run it

```bash
cd keelbase-java-crm-example
mvn spring-boot:run          # http://localhost:8082
```

Seed data: two customers (one `ACTIVE`, one `RISK`) + three orders (two `OVERDUE`) — enough for the AI risk-analysis demo.

## Wire into KeelBase

```bash
# 1. Export the ai_proxy_tools config (5 CRM tools)
curl http://localhost:8082/keelbase/proxy-tools/export

# 2. PUT the exported JSON into KeelBase Settings (value = the JSON string), restart KeelBase
#    Management console → Settings → ai_proxy_tools (or PUT /api/v1/settings/ai_proxy_tools)

# 3. Full loop (KeelBase + this module running):
node scripts/verify-crm-e2e.mjs --configure   # export + write config (then restart KeelBase)
node scripts/verify-crm-e2e.mjs --verify      # confirmation gate → approve → write back → audit → revoke → compensation
```

## The governed AI loop

```
You: "Which customers deserve follow-up?"

AI:
  1. list_customers (R1, auto) → find RISK customer 天穹科技
  2. list_customer_orders (R1, auto) → spot the overdue 980,000 order
  3. risk analysis → propose create_followup_task
  4. confirmation gate (R3) — not executed without your approval
  5. approve → proxy writes back to the Java CRM with delegated identity (createdBy = your user)
  6. audit hash chain records it; revoke → compensation endpoint soft-deletes (idempotent)
```

## Same domain, two integration paths

| | B-path OpenAPI proxy | This Java implementation |
|---|---|---|
| Declaration | `external-crm.openapi.json` → `keelbase init --import-openapi-proxy` | `@KeelbaseTool` on real endpoints |
| Tool set | identical 5 tools | identical 5 tools |
| Write gate | R3 (same) | R3 (same) |
| Revoke | revokePath → Java compensation | `KeelBaseCompensationSupport` (idempotent + audit) |

The two are drop-in interchangeable from KeelBase's perspective — the AI tools are the same, only the declaration surface differs (annotation on real Java vs. OpenAPI import).

## Configuration

See [configuration](configuration.md) — `keelbase.delegation.*` (shared `DELEGATION_SECRET`, `audience: legacy-crm`) and `keelbase.tools.base-url` (server root `http://localhost:8082`). The compensation path `/api/compensation` is fail-closed (no anonymous revocation).
