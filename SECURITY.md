# Security Policy / 安全政策

KeelBase-java-starter is the Java/Spring Boot bridge for [KeelBase](https://github.com/rain6fish/KeelBase) — it lets AI operate your existing business systems under KeelBase governance. Because it sits on the **trust boundary** (delegated identity + revocation), we take security seriously.

KeelBase-java-starter 是 [KeelBase](https://github.com/rain6fish/KeelBase) 的 Java/Spring Boot 桥接组件——让 AI 在 KeelBase 治理下操作你的存量业务系统。由于它位于**信任边界**（委托身份 + 撤销），我们高度重视安全。

---

## Supported Versions / 受支持的版本

This project is under active development (pre-1.0). Security fixes are applied to the `main` branch and released with the next version.

本项目处于活跃开发期（1.0 之前）。安全修复会应用到 `main` 分支并随下一版本发布。

| Version | Supported / 支持 |
|---------|-----------|
| main (development) | ✅ Latest fixes applied / 应用最新修复 |

---

## Reporting a Vulnerability / 漏洞报告

**Please do not open a public GitHub issue for security vulnerabilities.**

**安全漏洞请勿直接提交公开 GitHub Issue。**

### Preferred: Private disclosure / 首选：私密披露

Send an email to: **[128766028+rain6fish@users.noreply.github.com](mailto:128766028+rain6fish@users.noreply.github.com)**

Please include / 请在你的报告中包含：

- **Affected module(s)** / 受影响模块（`keelbase-tools-annotation` / `keelbase-delegation-filter` / `keelbase-tools-export` / `keelbase-compensation` / `keelbase-spring-boot-autoconfigure` / `keelbase-java-example`）
- **Vulnerability type** / 漏洞类型（e.g. signature bypass, audience confusion, idempotency bypass, information disclosure…）
- **Steps to reproduce** / 复现步骤（尽量最小化）
- **Impact** / 影响范围
- **Affected version(s)** / 受影响版本

### What happens next / 后续流程

1. We acknowledge receipt within **72 hours** / 我们在 72 小时内确认收到
2. We investigate, confirm, and assess severity / 我们调查、确认并评估严重性
3. We develop a fix and coordinate disclosure with you / 我们开发修复并与你协调披露
4. After a fix is released, we credit you in the release notes (if you wish) / 修复发布后，我们会按你的意愿在发布说明中致谢

---

## Scope / 范围

We consider the following in scope for security review / 以下内容在安全审查范围内：

- **Delegation JWT verification**（`DelegationAuthFilter`）：HS256 signature, `exp` expiry, `aud`/`iss` validation, secret strength（≥32 bytes）。
- **Path protection semantics**：fail-open vs protected-path fail-closed behavior, and SSRF-style abuse of forwarded calls.
- **Proxy-tool export**（`ProxyToolsScanner` / `/keelbase/proxy-tools/export`）：parameter/type inference must not leak or mis-authorize.
- **Compensation scaffold**（`KeelBaseCompensationSupport`）：idempotency guarantees, delegated-identity check on revocation, audit.
- **Secrets handling**：the delegation secret must never be logged or exposed.

Out of scope / 不在范围内：the KeelBase core runtime itself (report those to [KeelBase SECURITY](https://github.com/rain6fish/KeelBase/blob/main/SECURITY.md)), and your application's own business logic.

---

## Security posture / 内置安全姿态

| 能力 | 说明 |
|---|---|
| 委托 JWT | HS256（`DELEGATION_SECRET`，≥32 字节，缺省启动即失败）+ `aud`/`iss`/`exp` 三重校验 |
| 鉴权语义 | 无头放行（fail-open）+ 受保护路径 fail-closed（`keelbase.delegation.paths`） |
| 映射 SPI | `KeelBaseUserMapper` 自定义身份映射；默认 oidcSub 优先 |
| 补偿幂等 | `RevocationLedger` LRU 账本（可接持久化 Store），重复撤销返回 `idempotent:true` |
| 审计 | `CompensationAuditSink` 记录 who/when/resultId，与 KeelBase 审计链呼应 |
| 密钥卫生 | 不在日志打印 token/secret；过滤器支持 `@ConditionalOnMissingBean` 覆盖 |
