# 参考项目——传统 Java CRM → AI CRM（Java 侧真实实现）

**Integrator Kit Reference Project**：一个存量 Java CRM 接入 KeelBase 成为治理 AI 工具。这是 `external-crm-demo.md`（KeelBase 主仓库，B 路径 OpenAPI 代理）描述的 CRM 域的 **Java 侧真实实现**——5 个工具、风险级、治理闭环完全对齐。集成商按[8 步实施手册](../../docs/integrator-kit/reference-project-guide.md)照做，本模块就是那个"存量系统"。

> Reference Project for integrators: the real Java side of the legacy-Java-CRM → AI-CRM domain, aligned with `external-crm-demo.md` (B-path OpenAPI proxy).

## 域

| 工具 | 端点 | 风险级 | 治理 |
|---|---|---|---|
| `list_customers` | `GET /api/customers?keyword=` | R1 | 自动执行（读） |
| `get_customer` | `GET /api/customers/{id}` | R1 | 自动执行（读） |
| `list_customer_orders` | `GET /api/customers/{id}/orders` | R1 | 自动执行（读） |
| `create_followup_task` | `POST /api/customers/{id}/followups` | **R3** | **需人工确认** + 可撤销 |
| `update_order_amount` | `PATCH /api/customers/{id}/orders/{orderId}` | **R3** | **需人工确认**（改价不可逆 → 撤销诚实语义） |

## 运行

```bash
cd keelbase-java-crm-example
mvn spring-boot:run          # http://localhost:8082
```

种子数据：两个客户（`ACTIVE` / `RISK`）+ 三笔订单（两笔 `OVERDUE`）——足够 AI 风险分析演示。

## 接入 KeelBase

```bash
# 1. 导出 ai_proxy_tools 配置（5 个 CRM 工具）
curl http://localhost:8082/keelbase/proxy-tools/export

# 2. 把导出 JSON 写入 KeelBase Settings（value = JSON 字符串），重启 KeelBase
#    管理台「设置」→ ai_proxy_tools（或 PUT /api/v1/settings/ai_proxy_tools）

# 3. 完整闭环（KeelBase + 本模块运行）：
node scripts/verify-crm-e2e.mjs --configure   # 导出 + 写配置（然后重启 KeelBase）
node scripts/verify-crm-e2e.mjs --verify      # 确认门控 → 批准 → 写回 → 审计 → 撤销 → 补偿
```

## 受治理的 AI 闭环

```
你：「哪些客户值得跟进？」

AI：
  1. list_customers（R1 自动）→ 找到 RISK 客户天穹科技
  2. list_customer_orders（R1 自动）→ 发现 98 万逾期订单
  3. 风险分析 → 建议 create_followup_task
  4. 确认门控（R3）——不批准不执行
  5. 批准 → proxy 以委托身份写回 Java CRM（createdBy = 你的用户）
  6. 审计哈希链落账；撤销 → 补偿端点软删（幂等）
```

## 同一域，两种接入

| | B 路径 OpenAPI 代理 | 本 Java 实现 |
|---|---|---|
| 声明 | `external-crm.openapi.json` → `keelbase init --import-openapi-proxy` | 真实端点加 `@KeelbaseTool` |
| 工具集 | 相同 5 个 | 相同 5 个 |
| 写门控 | R3（同） | R3（同） |
| 撤销 | revokePath → Java 补偿 | `KeelBaseCompensationSupport`（幂等 + 审计） |

从 KeelBase 视角两者可互换——AI 工具相同，只是声明方式不同（真实 Java 注解 vs OpenAPI 导入）。

## 配置

见[配置参考](configuration.zh-CN.md)——`keelbase.delegation.*`（共享 `DELEGATION_SECRET`、`audience: legacy-crm`）与 `keelbase.tools.base-url`（服务器根 `http://localhost:8082`）。补偿路径 `/api/compensation` fail-closed（匿名无法撤销）。
