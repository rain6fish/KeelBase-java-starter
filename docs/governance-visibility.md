# Governance Visibility — 治理可见性

Your Java tools registered via `@KeelbaseTool` become visible in the KeelBase governance plane the moment the exported `ai_proxy_tools` config is applied. This page maps where each piece of Java tool governance shows up, plus how to self-inspect from the Java side without touching the KeelBase console.

> Java 侧 `@KeelbaseTool` 导出的工具，一经写入 `ai_proxy_tools` 配置，即在 KeelBase 治理台可见。本文说明治理可见的落点 + Java 侧自检（不依赖治理台）。

## 治理台可见（KeelBase 管理台）

| 治理台页面 | 看到的 Java 侧内容 |
|---|---|
| 工具与副作用 | 工具清单 + 风险分级（读 R1 / 写 R3）+ 确认门控状态 |
| AI 审计 | Java 工具调用 / 确认决策 / 撤销操作（防篡改审计哈希链） |
| 风险中心 | 工具风险面 + 阻断/拒绝态势 |
| AI 行为回放 | 单次 AI 会话中 Java 工具的执行轨迹 |

## Java 侧自检（本地 / 无治理台）

`GET /keelbase/status` 升级为**接入健康度面板**，Java 团队本地即可看到治理面：

```json
{
  "tools": {
    "count": 5,
    "riskDistribution": { "R1": 3, "R3": 2 },   // 治理面：读自动 / 写确认
    "revokeCovered": 2                           // 补偿覆盖：可撤销的写工具数
  },
  "health": { "status": "healthy", "summary": "接入配置完整，工具可正常导出" }
}
```

一键自检（不依赖 KeelBase 后端）：`node scripts/verify-java-local.mjs <baseUrl>`——验委托验签、工具契约、受保护路径门控，输出 PASS/FAIL 接入诊断。

## 参考项目可视化

三个参考项目（CRM / PM / Approval）种子数据对齐旗舰，接入后治理台立即可见其工具治理效果——见 [reference-project-crm](reference-project-crm.md) / [-pm](reference-project-pm.md) / [-approval](reference-project-approval.md)。
