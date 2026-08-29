# 生产上线核对清单

把已接入 KeelBase 的 Java/Spring 系统上线前过一遍。假设开发态已按[快速开始](quickstart.zh-CN.md)跑通。

## 1. 上线前（开发态）

- [ ] `keelbase.delegation.secret` 已配置（≥ 32 字节），与 KeelBase 的 `DELEGATION_SECRET` 完全一致。
- [ ] `keelbase.delegation.audience` 已配置，等于 `ai_proxy_tools` 顶层 `audience`。
- [ ] `GET /keelbase/status` 显示 `delegation.secretConfigured: true`、`export.audience` 正确、**无告警**。
- [ ] `keelbase.tools.base-url` 是**服务器根**（不带 path 前缀），且 KeelBase 所在主机可达。
- [ ] 补偿 / `revokePath` 端点已列入 `keelbase.delegation.paths`（fail-closed，杜绝匿名可达）。
- [ ] 完整闭环已通过：`scripts/verify-java-starter-e2e.mjs --configure` + `--verify`。

## 2. 上线前硬化

- [ ] **独立 `DELEGATION_SECRET`** —— KeelBase 未配时回退 `JWT_SECRET`，请单独配置并在 Java 侧保持一致；轮换它不应影响用户 token。
- [ ] **每个目标系统一个 `audience`** —— 它是系统间信任边界，不要多个无关系统共用一个值。
- [ ] **注册完成后关闭导出端点** —— `keelbase.tools.export-enabled: false`，或在网关/防火墙限制 `/keelbase/proxy-tools/*`。它会暴露你的内部工具路径。
- [ ] **决定 `keelbase.tools.status-enabled`** —— 运维用得上有诊断价值就保持 `true`（绝不泄露密钥）；高安全环境设 `false`。
- [ ] **保持委托 TTL 短**（KeelBase 缺省 300s）。撤销由补偿端点负责，不靠长活 token。
- [ ] **记住 starter 是增量** —— `keelbase.delegation.paths` 之外默认 fail-open，非 KeelBase 流量仍靠你原有的认证；受保护路径的 fail-closed 是按前缀显式开启的。

## 3. 运维

- [ ] 关注日志里的 `delegation.invalid` / `delegation.expired` 突增——密钥轮换或配置变更后出现峰值通常是两侧漂移的信号。
- [ ] **密钥轮换**：KeelBase 与 Java 侧同步更新；用 `GET /keelbase/status`（`secretConfigured` + 一次委托调用）验证。
- [ ] **审计**：抽查 KeelBase 控制台里的 AI 写副作用与撤销记录，确认你侧的 `CompensationAuditSink` 条目与之对应。
- [ ] 任何配置变更后重跑 `GET /keelbase/status`，作为单一健康检查入口。

## 4. 回滚 / 排障

委托调用开始失败时：

1. `GET /keelbase/status` —— `secretConfigured` 是否 true？有无 `warnings`？
2. KeelBase 侧 —— `GET /api/v1/ai/tools` 是否仍显示代理工具（配置在启动时读取）？
3. 失败调用是否命中了受保护路径却无 token，或 token 的 `audience`/`issuer` 已不再匹配？
4. 按错误码查[排障](troubleshooting.zh-CN.md)。
