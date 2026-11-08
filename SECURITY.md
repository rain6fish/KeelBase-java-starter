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

---

## Dependency security / 依赖安全

This repository pins three dependencies above the versions Spring Boot 3.5.16 ships by default, because 3.5.16 is the newest release of that line and no later patch exists to take.

本仓把三个依赖钉在 Spring Boot 3.5.16 自带版本之上：3.5.16 已是该线最新版，没有更晚的补丁可取。

| Dependency / 依赖 | Boot 3.5.16 | Pinned here / 本仓固定 |
|---|---|---|
| `com.fasterxml.jackson.core:jackson-databind` | 2.21.4 | **2.21.7** |
| `org.apache.logging.log4j:log4j-api` | 2.24.3 | **2.25.5** |
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.1.55 | **10.1.61** |

The pins live in the parent `pom.xml` (imported ahead of the Boot BOM, where the first import wins) and in `build.gradle` (explicit version constraints, because Gradle resolves to the highest version instead of honouring import order). They apply to what this starter brings in transitively. **If your application manages Spring Boot's BOM itself, declare the same overrides in your own `dependencyManagement`** — your own managed versions take precedence over ours.

固定点写在父 `pom.xml`（排在 Boot BOM 之前导入，先声明者生效）与 `build.gradle`（显式版本约束，因为 Gradle 取最高版本而不遵循导入顺序）。它们作用于本 starter 带入的传递依赖。**如果你的应用自己管理 Spring Boot BOM，请在你自己的 `dependencyManagement` 里声明同样的覆写** —— 你自己管理的版本优先于我们的。

### What is still open / 仍然开放的公告

Two Spring Framework advisories cannot be fixed on this line:

`CVE-2026-47890` (SSE stream corruption while rendering fragments) and `CVE-2026-47884` (improper path limitation in `XsltView`) affect Spring Framework **6.2.0 through 6.2.19**. 6.2.19 is the final release of the 6.2 line; the fix exists only in **7.0.9**, and therefore only in **Spring Boot 4**. There is no patched 6.2.x to upgrade to.

两条 Spring Framework 公告无法在本线修复：

`CVE-2026-47890`（渲染 fragment 时 SSE 流损坏）与 `CVE-2026-47884`（`XsltView` 路径限制不当）影响 Spring Framework **6.2.0 至 6.2.19**。6.2.19 是 6.2 线的最后一版；修复只存在于 **7.0.9**，因而只在 **Spring Boot 4** 中可用。不存在已修补的 6.2.x 可升级。

**Reachability / 可达性.** Both require a specific MVC feature: server-sent events, or `XsltView`. Neither this starter nor its bundled examples uses either: `grep -r "SseEmitter\|XsltView"` over this repository returns nothing. An application that does use SSE or `XsltView` is exposed and should plan the Spring Boot 4 upgrade; an application that uses neither is not reachable through this bug in the starter's own code.

**可达性。** 两者都需要特定的 MVC 能力：服务端事件（SSE）或 `XsltView`。本 starter 与其内置示例都未使用 —— 在本仓 `grep -r "SseEmitter\|XsltView"` 无结果。使用 SSE 或 `XsltView` 的应用会受影响，应规划升级 Spring Boot 4；两者都不用的应用，无法经由本 starter 自身代码触达该缺陷。

### Checking your own application / 自查

Run against your application's dependency tree:

对你的应用依赖树运行：

```bash
osv-scanner scan source -r .
```

Expect to see `spring-webmvc 6.2.x` if you are on Spring Boot 3.5.x, and nothing else that this repository introduced.

若你在 Spring Boot 3.5.x 上，应当看到 `spring-webmvc 6.2.x`，以及本仓引入的其它项应当为零。
