# 参考项目——传统 Java 审批流 → AI Approval（Java 侧真实实现）

**Integrator Kit Reference Project（审批）**：一个存量 Java 审批系统接入 KeelBase 成为治理 AI 工具。对齐 KeelBase AI Approval 旗舰（AI 预审 + 人工复核）——读自动（R1）、审批决定需人工确认（R3）、经补偿端点可撤销，与 [CRM](reference-project-crm.zh-CN.md)/[PM](reference-project-pm.zh-CN.md) 参考项目同一治理闭环。

> Reference Project for integrators: the real Java side of the legacy-Java-approval → AI Approval domain, aligned with the AI Approval flagship (AI pre-audit + human review).

## 域

| 工具 | 端点 | 风险级 | 治理 |
|---|---|---|---|
| `query_approval_requests` | `GET /api/approval-requests` | R1 | 自动执行（读） |
| `get_approval_request` | `GET /api/approval-requests/{id}` | R1 | 自动执行（读） |
| `decide_approval_request` | `PATCH /api/approval-requests/{id}/decision` | **R3** | **需人工确认** + 可撤销 |

种子数据对齐 AI Approval 旗舰：¥800 差旅（低于阈值 → 自动通过）+ ¥12000 采购（→ 转人工复核）。

## 运行

```bash
cd keelbase-java-approval-example
mvn spring-boot:run          # http://localhost:8084
```

## 接入 KeelBase

```bash
# 1. 导出 ai_proxy_tools 配置（3 个审批工具）
curl http://localhost:8084/keelbase/proxy-tools/export

# 2. 把导出的 JSON 写入 KeelBase Settings（value = JSON 字符串），重启 KeelBase
#    (PUT /api/v1/settings/ai_proxy_tools, audience = legacy-approval)

# 3. 完整闭环（KeelBase + 本模块运行）：
node scripts/verify-approval-e2e.mjs --configure   # 导出 + 写配置（然后重启 KeelBase）
node scripts/verify-approval-e2e.mjs --verify      # 确认门控 → 批准 → 委托身份写回 → 审计 → 撤销补偿
```

## 受治理的 AI 闭环

```
你：「预审一下待审批的请求。」

AI：
  1. query_approval_requests（R1 自动）→ 找到 ¥800 差旅 + ¥12000 采购
  2. 按政策预审 → 提议 decide_approval_request（approve）
  3. 确认门控（R3）——未经你批准不执行
  4. 批准 → proxy 以委托身份写回 Java 审批流（decidedBy = 你的用户）
  5. 审计哈希链记录；撤销 → 补偿端点恢复请求为 pending（幂等）
```

## 委托身份写回

`decide_approval_request` 接收 `@DelegationUser DelegationPrincipal`，落 `decidedBy = principal.identity()`（你的 `oidcSub`，或剥掉 `local:` 前缀的本地用户 id；无委托 token 时回退 `anonymous`）。KeelBase 的"携身份治理"由此延伸到存量 Java 系统——决定知道是谁（经 AI）批准，且撤销决定会清空 `decidedBy` 恢复 `pending`。由 `ApprovalCompensationTest` 验证（决定 → 断言 decidedBy + 状态 → 撤销 → 幂等 → 恢复 pending → 401 fail-closed）。

## 同一域、两条接入路径

| | B 路径 OpenAPI 代理 | 本 Java 实现 |
|---|---|---|
| 声明方式 | `specs/external-approval.openapi.json` → `keelbase init --import-openapi-proxy` | 真实端点 `@KeelbaseTool` |
| 工具集 | 相同 3 个审批工具 | 相同 3 个审批工具 |
| 写门控 | R3（相同） | R3（相同） |
| 撤销 | revokePath → Java 补偿 | `KeelBaseCompensationSupport`（幂等 + 审计） |

从 KeelBase 视角两者可互换——AI 工具相同，仅声明面不同（真实 Java 注解 vs OpenAPI 导入）。

## 健康检查

`GET /keelbase/status` 报告 delegation/export/tools/health——与其他参考项目相同的诊断。

## 配置

见 [configuration.zh-CN.md](configuration.zh-CN.md)——`keelbase.delegation.*`（共享 `DELEGATION_SECRET`，`audience: legacy-approval`）+ `keelbase.tools.base-url`（服务根 `http://localhost:8084`）。补偿路径 `/api/compensation` fail-closed（不接受匿名撤销）。
