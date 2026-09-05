# Changelog

All notable changes to this project are documented in this file. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning follows [SemVer](https://semver.org/).

## [Unreleased]

### Added

- **脚手架默认带契约测试** — `keelbase-java-skeleton` 生成的项目自动含 `{{Domain}}ContractTest`（继承 `keelbase-test-support` 的 `KeelbaseContractTest`）+ pom 加 test-support 依赖 + `application.yml` secret 本地默认样例——新项目从第一天 `mvn test` 即守护接入合规（导出契约 / 受保护路径 401 / 委托 JWT 2xx），与 ci-template.yml 闭环。生成项目实测契约测试 3/3 绿。

### Fixed

- **skeleton Controller 路径 bug** — 类 `@RequestMapping("/api/items")` + 方法 `/items`、`/compensation/items/{id}` 使真实端点为嵌套的 `/api/items/items`、`/api/items/compensation/…`，与 `revokePath` 声明 `/api/compensation/items/{id}` 不符（委托撤销 404）；类前缀改 `/api`（对齐 example 模式），真实端点为 `/api/items` + `/api/compensation/items/{id}`，契约测试 `protectedPathAcceptsDelegatedToken` 通过。

## [0.1.7] - 2026-09-03

> 0.1.7 — Java 接入可观测与合规：actuator 健康指示器 / 治理台策略实时拉取 / CI 接入合规模板。

### Added

- **actuator HealthIndicator** — `/keelbase/status` 接入 Spring Boot actuator `/health`（`keelbase.health.enabled`，需消费者自加 `spring-boot-starter-actuator`）：healthy/degraded→`UP`（degraded 带 warnings）、error→`DOWN`；`KeelbaseHealthIndicator` 复用 `KeelbaseStatusController.status()` 健康判定，不重写规则。
- **治理策略拉取** — `KeelbasePolicyClient`（治理台 `GET /api/v1/external/governance/policy` + x-api-key 服务身份）拉取实时治理策略（工具 override / 审计粒度），业务系统本地按策略约束执行；复用 `keelbase.audit.base-url/api-key`；未配置 → `Optional.empty()`。`GovernancePolicy` record + autoconfigure 注册 bean。
- **CI 接入合规模板** — `ci-template.yml` + `docs/ci-integration.md`/`ci-integration.zh-CN.md`：消费方复制即得「契约测试 + 本地自检 + 导出门禁」双 job，接入合规持续守护。

## [Unreleased]

### Added

（待发布）

## [0.1.6] - 2026-09-01

### Added

- **Gradle 团队体验** — 抽 `ProxyToolsExportClient`（纯 JDK + Jackson，Maven 插件 / Gradle task / CLI 三处复用）+ `KeelbaseToolsCli` + `gradle/keelbase.gradle` 模板：`./gradlew keelbaseExport` / `keelbaseRegister` 与 `mvn keelbase:export/register` 对齐（导出 → 写 Settings → 热更新免重启）。Maven 插件重构为薄壳。
- **启动 fail-fast 校验** — `keelbase.tools.strict=true`：`@KeelbaseTool` 声明非法（无法解析 method/path、工具名非法）启动即失败并列出明细（`ToolsExportValidator` + `ProxyToolsScanner.scanWithReport`），替代默认只 WARN 跳过导致的「导出缺工具」困惑。
- **JUnit 契约测试基类** — `keelbase-test-support`：`KeelbaseContractTest` 抽象类（`@SpringBootTest` 继承即断言导出契约 / 受保护路径 401 / 委托 JWT 2xx 幂等），把接入合规验证内建进 Java 团队自己的 CI；example 附 `ContractComplianceTest`。
- **Spring 脚手架** — `keelbase-java-skeleton` 模板 + `scripts/new-keelbase-project.mjs`：一条命令生成「读 R1 + 写 R3 + 补偿端点」就绪的 Spring Boot 项目（starter 版本自动从根 pom 读取），development-guide 加「从脚手架开始」。

## [0.1.5] - 2026-09-01

### Added

- **Maven 导出/注册插件** — `keelbase-maven-plugin`：`mvn keelbase:export`（导出 + 校验 `ai_proxy_tools` 到文件，默认绑定 verify 阶段）/ `mvn keelbase:register`（admin 登录 → 导出 → 写 KeelBase Settings）；复用运行时 `GET /keelbase/proxy-tools/export` 端点，`docs/maven-plugin{,.zh-CN}.md`。Maven-only（Gradle 团队走 HTTP/脚本）。
- **一键演示脚本** — `scripts/demo-starter.mjs`：一条命令跑通「起 example → 接入自检 → 导出 → 写配置 → 完整闭环」（`--start-example` 后台起示例）；quickstart 加「一键演示」小节。

### Changed

- **热更新配套** — `verify-java-starter-e2e.mjs` configure 提示改为「KeelBase 热更新生效（免重启）」（配合主仓 ai_proxy_tools 免重启热更新）。

## [0.1.4] - 2026-09-01

### Added

- **Apache-2.0 SPDX 许可头全量补齐** — 全仓 84 个源码文件（.java + scripts 的 .sh/.mjs）加 `SPDX-License-Identifier: Apache-2.0`（幂等脚本 `scripts/add-license-headers.mjs` 可复用，`--check` 校验），对齐主仓库开源可信度包装。
- **架构与数据流文档** — `docs/architecture{,.zh-CN}.md`：总览「控制面/数据面」定位、B 路径接入选路、三条数据流时序（AI→Java 调用 / 写+确认+撤销补偿 / Java→KeelBase 反向）、委托 JWT 身份桥细节与治理边界。

## [0.1.3] - 2026-08-31

### Added

- **`keelbase/status` 接入健康度面板** — tools 加 riskDistribution（R1/R3 分布）+ revokeCovered（补偿覆盖）+ audit 上报状态（configured 布尔，不泄 api-key）+ overall health（healthy/degraded/error + 中文 summary）+ 告警分级（errors 阻断 / warnings 提示，含 export 关闭 / 写无 revokePath / audit 未配置提示）。
- **零样板导出** — 无 springdoc `@Parameter`/枚举/默认值的 `@RequestParam`/`@PathVariable` 自动生成语义描述（`customerId` → `customer ID（integer）`，camelCase 分词 + 类型）。
- **PM 参考项目** — `keelbase-java-pm-example`：存量 Java PM 样板（读 R1 自动 + `create_pm_task` 写 R3 确认 + revokePath 补偿），对齐 AI Project 旗舰延期风险分析。
- **审批参考项目** — `keelbase-java-approval-example`：存量 Java 审批流样板（AI 预审 + 人工复核语义，¥800 自动通过 / ¥12000 转人工；`decide_approval_request` 写 R3 + 可撤销恢复待审）。
- **本地调试** — `scripts/verify-java-local.mjs`：不依赖 KeelBase 的接入自检（委托验签 / 工具契约 / 受保护路径门控 / audit 状态，PASS/FAIL 诊断）。
- **治理可见性** — `docs/governance-visibility.md`：Java 工具在治理台工具/审计/风险中心的落点 + status 健康度治理面。
- **Boot 2 / Java 8 适配** — `docs/boot2-java8-adapter.md`：存量 Java 栈双路径（A starter Boot3+Java17 / B API 代理零代码任意栈）。

### Fixed

- **Gradle 构建缺失** — pm/approval 补 `build.gradle`（settings.gradle include 后模块缺构建文件）。
- **四层 code review 修复** — 健康度补审计上报（Spec 阻塞）/ export 关闭健康明确 WARN / `PmTask.markDone` 死代码删除 / revokeCovered 重复计算合并 / approval secret 生产警告注释。

## [0.1.2] - 2026-08-30

### Added

- **`keelbase-client`** — `KeelbaseClient` (delegation-token lifecycle: `obtain` / `obtainAndCache` with proactive refresh / local `verify`, audience-bound) + `KeelbaseAuditReporter` (async audit reporting to the governance plane, D2-3a `/external/audit`, local-log fallback when unconfigured).
- **`keelbase-java-crm-example`** — Integrator Kit Reference Project: a legacy Java CRM (customers/orders/follow-ups) declared as 5 governed AI tools (read R1 / write R3 + revocable `create_followup_task`), the real Java side of the `external-crm-demo` CRM domain, with `verify-crm-e2e.mjs` and `docs/reference-project-crm{,.zh-CN}.md`.
- **Class-level `@KeelbaseTool`** — `@KeelbaseTool` now also targets `TYPE`: a whole `@RestController` is tooled up in one go (tool name = method `camelCase → snake_case`), with method-level `enabled=false` to exclude helper/internal endpoints. Method-level attributes override class-level. Demoed by `keelbase-java-crm-example`'s `CrmInsightsController` (3 tools + 1 excluded).
- **Export param description** — `@RequestParam` parameters now export enum allowed values (`可选: A/B/C`) and explicit defaults (`默认: x`) into the tool parameter `description`, matching the `@RequestBody` enum description so the LLM sees precise options.
- **Spring Boot 3.5.16** — dependency BOM + Gradle plugin upgraded from 3.2.5 to 3.5.16 (Spring Framework 6.2); full Maven + Gradle builds green.
- **Swagger/OpenAPI doc extraction** — when springdoc annotations are on the classpath, tool descriptions come from `@Operation(summary/description)`, parameter descriptions from `@Parameter(description)`, and `@RequestBody` field descriptions from `@Schema(description)` (reflection-based, no hard dependency), so `@KeelbaseTool`/`@Schema` descriptions need not be repeated.
- **crm-example springdoc demo** — the CRM sample annotates `CrmInsightsController` (class-level `@KeelbaseTool` + per-method `@Operation`) and `CrmController.listCustomers` (`@Parameter`), proving class-level tools and params pick up descriptions automatically; `CrmExportTest` asserts them.
- **crm-example pagination** — `list_customers` now paginates: `page`/`limit` params (with `@Parameter` descriptions + defaults exported, e.g. `页码（从 1 起）；默认: 1`) and a stable `{items,total,page,limit}` response; `CrmExportTest` asserts the pagination params.
- **Tool patterns guide** — `docs/tool-patterns{,.zh-CN}.md`: recipes for pagination, keyword filters, enum params, springdoc descriptions, class-level tools, and write+revoke, each pointing at a working example.
- **crm-example batch write** — `batch_create_followups` demonstrates bulk writes with a nested array body that exports as a `string` param (JSON array text); `CrmExportTest` asserts it, and the tool-patterns guide gains a batch-write section.
- **Deterministic export order** — the scanner now sorts exported tools by name (the underlying `RequestMappingHandlerMapping` iteration order is unstable across restarts), so `ai_proxy_tools` diffs/audits are stable; `CrmExportTest` asserts the sorted order.
- **Gradle consumer guide** — `docs/gradle-usage{,.zh-CN}.md`: dependency block, `application.yml`, version table, and building from source via Gradle.
- **`KeelbaseClient.verifyAndGet`** — after verifying, returns the parsed identity (`subject` / `oidcSub` / `audience` / `expiresAt`) so callers can map the delegation subject to a local user.

## [0.1.1] - 2026-08-30

### Added

- **`@RequestParam` 枚举/默认值描述** — 参数描述透传枚举可选值（`可选: A/B/C`）与显式默认值（`默认: x`），对齐 `@RequestBody` 枚举口径。
- **CRM 参考项目** — `keelbase-java-crm-example`：存量 Java CRM 样板（5 个治理工具，读 R1 自动 / 写 R3 确认 + revokePath 撤销），域与 `external-crm-demo` 对齐。
- **Spring Boot 3.5.16** — BOM/Gradle 插件升级（Spring Framework 6.2）。

### Fixed

- **Central 发布校验和** — 上传包补 `.md5`/`.sha1`（0.1.0 首发因缺校验 FAILED 的根因），发布流程文档化。

## [0.1.0] - 2026-08-29

> First release published to Maven Central (`cn.com.keelbase:*`), uploaded via the Central Portal with GPG signing (key `7ECAABC1ABDC27F3`).

### Added

- **`keelbase-tools-annotation`** — `@KeelbaseTool`, `KeelbaseRiskLevel` (R0-R5), `@EnableKeelbaseTools`.
- **`keelbase-delegation-filter`** — `DelegationAuthFilter` (HS256 + aud/iss/exp verification; fail-open with protected-path fail-closed), `@DelegationUser` argument injection, `KeelBaseUserMapper` SPI, optional Spring Security adapter.
- **`keelbase-tools-export`** — annotation scanner + type mapping aligned with the KeelBase generator, `GET /keelbase/proxy-tools/export`.
- **`keelbase-compensation`** — `KeelBaseCompensationSupport` scaffold, `RevocationLedger` idempotency ledger, `CompensationAuditSink`.
- **`keelbase-spring-boot-autoconfigure` / `keelbase-spring-boot-starter`** — auto-configuration and aggregator modules.
- **`keelbase-java-example`** — reference Spring Boot app (read R1 / write R3 with revokePath / compensation endpoint).
- **E2E verification** — `scripts/verify-java-starter-e2e.mjs` (configure + verify), validated against a real KeelBase: confirmation gate → streaming approve → proxy write with delegated identity → audit → revoke → compensation.
- **Diagnostic endpoint** — `GET /keelbase/status` reports delegation config, resolved export audience, tool count, and configuration warnings (audience mismatch, missing base-url, no tools); never leaks the secret. Controlled by `keelbase.tools.status-enabled`.
- **Config resolver** — `ExportConfigResolver`: single `audience` source of truth (`keelbase.tools.audience` falls back to `keelbase.delegation.audience`), `base-url` trailing-slash normalization, and export validation that returns a clear 500 when `base-url`/`audience` are missing.
- **Jackson-aware body parameter extraction** — `RequestBodyFields`: inherited DTO fields included, `@JsonIgnore`/static/transient skipped, `@JsonProperty` name & `required` respected, records supported.
- **Scanner warnings** — skipped tools (invalid name, unresolvable mapping) and auto-renamed name conflicts are logged instead of silently dropped.
- **Integration docs** — `docs/` quickstart / configuration / delegated-identity / tool-declaration / compensation / troubleshooting, bilingual (en + zh-CN).
- **Production checklist** — `docs/production-checklist.{md,zh-CN.md}`: hardening (dedicated secret, one audience per system, disable export), secret rotation, ops monitoring.
- **Gradle multi-module build** — `settings.gradle` + root/module `build.gradle` mirroring the poms (`java-library`, `api`/`compileOnly`/`runtimeOnly` scopes, Spring Boot BOM 3.2.5), Gradle wrapper, and a parallel CI job (`./gradlew build`). Maven remains the canonical CI/release build.
- **Example query & patch tools** — `list_followups_by_customer` (read tool with `@RequestParam` → query parameters) and `mark_followup_complete` (write tool with `@RequestParam` → `queryParams`), covering the read-query-param and write-`queryParams` paths.

### Fixed

- **Protected-path segment matching** — `keelbase.delegation.paths` now matches at path-segment boundaries (a prefix `/api/compensation` no longer protects `/api/compensations`).
- **Scanner dropped path/query params** — `MethodParameter`s from `HandlerMethod.getMethodParameters()` carry no `ParameterNameDiscoverer`, so `@PathVariable`/`@RequestParam` without an explicit name silently produced empty tool parameters. The scanner now injects `DefaultParameterNameDiscoverer`; exposed by the new query/patch example tools.
- **`@RequestParam` required inversion** — `required` was computed from an inverted default-value check; a param with no default was exported optional and one with a default required. Now: required = `@RequestParam` required **and** no explicit `defaultValue`.

- Verification script must **approve while streaming** — the confirmation gate suspends the SSE stream until a decision, so awaiting the full stream first lets the token expire (404).
- `baseUrl` convention is the **server root** + full tool `path` (avoids a doubled `/api` prefix).
- `DelegationAuthFilter` now also **requires `audience` at startup** (fail-fast with a clear message) instead of rejecting every delegated call at runtime with 403.
- Example integration test aligned with the server-root `baseUrl` convention (fixes the CI failure).
