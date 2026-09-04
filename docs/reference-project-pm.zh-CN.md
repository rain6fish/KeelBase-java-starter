# 参考项目——传统 Java PM → AI Project（Java 侧真实实现）

**Integrator Kit Reference Project（PM）**：一个存量 Java 项目管理接入 KeelBase 成为治理 AI 工具。对齐 KeelBase AI Project 旗舰（项目延期风险分析）——读自动（R1）、写需人工确认（R3）、经补偿端点可撤销，与 [CRM 参考项目](reference-project-crm.zh-CN.md) 同一治理闭环。

> Reference Project for integrators: the real Java side of the legacy-Java-PM → AI Project domain, aligned with the AI Project flagship (deadline-risk analysis).

## 域

| 工具 | 端点 | 风险级 | 治理 |
|---|---|---|---|
| `query_projects` | `GET /api/projects` | R1 | 自动执行（读） |
| `get_project` | `GET /api/projects/{id}` | R1 | 自动执行（读，含任务） |
| `create_pm_task` | `POST /api/projects/{id}/tasks` | **R3** | **需人工确认** + 可撤销 |

## 运行

```bash
cd keelbase-java-pm-example
mvn spring-boot:run          # http://localhost:8083
```

种子数据：三个项目（一个 `active/high`——如电商重构含逾期里程碑，足够 AI 延期风险演示）+ 任务。

## 接入 KeelBase

```bash
# 1. 导出 ai_proxy_tools 配置（3 个 PM 工具）
curl http://localhost:8083/keelbase/proxy-tools/export

# 2. 把导出的 JSON 写入 KeelBase Settings（value = JSON 字符串），重启 KeelBase
#    (PUT /api/v1/settings/ai_proxy_tools, audience = legacy-pm)

# 3. 完整闭环（KeelBase + 本模块运行）：
node scripts/verify-pm-e2e.mjs --configure   # 导出 + 写配置（然后重启 KeelBase）
node scripts/verify-pm-e2e.mjs --verify      # 确认门控 → 批准 → 委托身份写回 → 审计 → 撤销补偿
```

## 受治理的 AI 闭环

```
你：「哪些项目有延期风险？」

AI：
  1. query_projects（R1 自动）→ 找到 active/high 的「电商平台重构」
  2. get_project（R1 自动）→ 读取任务/里程碑
  3. 延期风险分析 → 提议 create_pm_task
  4. 确认门控（R3）——未经你批准不执行
  5. 批准 → proxy 以委托身份写回 Java PM（createdBy = 你的用户）
  6. 审计哈希链记录；撤销 → 补偿端点软取消任务（幂等）
```

## 委托身份写回

`create_pm_task` 接收 `@DelegationUser DelegationPrincipal`，落 `createdBy = principal.identity()`（你的 `oidcSub`，或剥掉 `local:` 前缀的本地用户 id；无委托 token 时回退 `anonymous`）。KeelBase 的"携身份治理"由此延伸到存量 Java 系统——记录知道是谁（经 AI）创建，且该身份在撤销后仍保留（取消的任务保留 `createdBy`）。由 `PmCompensationTest` 验证（写 → 断言 createdBy → 撤销 → 幂等 → 401 fail-closed）。

## 同一域、两条接入路径

| | B 路径 OpenAPI 代理 | 本 Java 实现 |
|---|---|---|
| 声明方式 | `specs/external-pm.openapi.json` → `keelbase init --import-openapi-proxy` | 真实端点 `@KeelbaseTool` |
| 工具集 | 相同 3 个 PM 工具 | 相同 3 个 PM 工具 |
| 写门控 | R3（相同） | R3（相同） |
| 撤销 | revokePath → Java 补偿 | `KeelBaseCompensationSupport`（幂等 + 审计） |

从 KeelBase 视角两者可互换——AI 工具相同，仅声明面不同（真实 Java 注解 vs OpenAPI 导入）。

## 健康检查

`GET /keelbase/status` 报告 delegation/export/tools/health——与 [CRM 参考项目](reference-project-crm.zh-CN.md) 相同的诊断。

## 配置

见 [configuration.zh-CN.md](configuration.zh-CN.md)——`keelbase.delegation.*`（共享 `DELEGATION_SECRET`，`audience: legacy-pm`）+ `keelbase.tools.base-url`（服务根 `http://localhost:8083`）。补偿路径 `/api/compensation` fail-closed（不接受匿名撤销）。
