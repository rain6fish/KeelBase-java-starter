# Changelog

All notable changes to this project are documented in this file. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning follows [SemVer](https://semver.org/).

## [0.2.0] - 2026-10-31

**English**

### Changed

- **Auto-DDL is opt-in.** `keelbase.compensation.jdbc.auto-ddl` now defaults to `false`: with
  `store=jdbc` the starter no longer issues DDL at startup. A missing `keelbase_revocation_ledger`
  fails startup with the key named and the DDL printed, instead of creating the table silently.
  Consumers who want the previous behaviour set `auto-ddl: true` explicitly. This is a behaviour
  change for existing JDBC-ledger deployments, and the reason this release is `0.2.0`.

### Added

- **The auto-configuration is proven conditional, and the ledger can use the host DataSource.**
  `ApplicationContextRunner` tests cover the whole starter: a complete configuration exposes every
  bean; a missing secret or audience fails startup by key; a user bean replaces the default ledger;
  the tools/delegation switches remove only their own beans; and non-servlet applications skip
  servlet-only auto-configuration. A new optional JDBC-backed revocation ledger
  (`keelbase.compensation.store=jdbc`) uses plain JDBC, so JPA, MyBatis and plain-JDBC hosts all work
  without an ORM API; `store=jdbc` without a DataSource or with a missing table fails startup naming
  the key, with no silent fallback to memory.

- **Zero-boilerplate Javadoc inference, default off.** `@KeelbaseTool(infer=true)` derives the tool
  description (Javadoc summary, then a readable method name), parameter descriptions (Javadoc
  `@param` and record-component tags, then a readable name-plus-type fallback), and exports bare
  simple-type handler parameters as optional query parameters. Javadoc is read reflectively through
  therapi-runtime-javadoc, so the starter gains no dependency; without the processor it silently
  falls back. Explicit declarations always win and governance semantics (risk level, revoke path,
  audience, enabled) are never inferred. With `infer` off every existing export is byte-identical,
  verified by sha256 against the CRM, PM, Approval and base example exports. Complex handler
  parameters that cannot become tool parameters are reported in `ScanReport.warnings()` instead of
  being dropped silently.

- **`Optional<T>` request parameters** are mapped by the wrapped type and exported optional, instead
  of as `string` with `required=true`.

### Fixed

- **The verification scripts no longer have false-green paths.** The PM script snapshots the
  project's task ids before the chat and requires a task that appeared in this run; the approval
  script requires request 1 to be `pending` before the run; both poll with a bounded deadline instead
  of one fixed sleep. The family-block gate counts raw `BEGIN` markers, so a malformed region next
  to a well-formed one can no longer hide. The CI matrix sets `fail-fast: false` so the JDK 21 leg
  reports even when 17 fails.

- **The Gradle build mirrors the Maven poms.** `keelbase-tools-export` now depends on
  `keelbase-client`; `keelbase-spring-boot-autoconfigure` has the actuator and the provided
  dependencies on both compile and test classpaths; `keelbase-test-support` is a Gradle module; the
  example contract test has its test-support dependency; and the H2 and therapi test dependencies
  are declared, with the scribe named as a test annotation processor. `./gradlew clean build` is
  green.

- **The Central publisher pins Maven 3.9.16** (the known 3.10.0 / central-publishing-plugin bundle
  bug) and gains `workflow_dispatch`, so a failed publish can be retried from `main` without moving
  a published tag.

**中文**

### 变更

- **建表改为显式开启。** `keelbase.compensation.jdbc.auto-ddl` 缺省改为 `false`：`store=jdbc` 时
  starter 不再在启动时执行 DDL。缺 `keelbase_revocation_ledger` 表会启动失败，错误里点名配置键并
  打印要执行的 DDL，而不是悄悄建表。需要旧行为者显式设 `auto-ddl: true`。这对已在用 JDBC 账本的
  部署是行为变更，也是本次发 `0.2.0` 的理由。

### 新增

- **自动配置条件化可证，账本可接宿主 DataSource。** `ApplicationContextRunner` 测试覆盖整个
  starter：配置齐全即暴露全部 bean；缺 secret 或 audience 时启动失败并点名；用户 bean 可覆盖默认
  账本；tools/delegation 开关只移除各自的 bean；非 servlet 应用跳过 servlet-only 自动配置。新增
  可选的 JDBC 账本（`keelbase.compensation.store=jdbc`），用普通 JDBC，JPA / MyBatis / 纯 JDBC
  宿主均可用且不依赖 ORM API；`store=jdbc` 但无 DataSource 或缺表时启动失败并点名配置键，不静默
  回退到内存。

- **零样板 Javadoc 推断，默认关闭。** `@KeelbaseTool(infer=true)` 推断工具描述（Javadoc 摘要，
  其次可读化方法名）、参数描述（Javadoc `@param` 与 record 组件标签，其次可读化「名称+类型」），
  并把裸简单类型 handler 参数导出为可选查询参数。Javadoc 通过 therapi-runtime-javadoc 反射读取，
  starter 不新增依赖；未接入处理器时静默退回。显式声明始终优先，治理语义（风险级、撤销路径、
  audience、enabled）永不推断。`infer` 关闭时所有存量导出逐字节不变，已对 CRM、PM、审批与基础示例
  四份导出用 sha256 复验。无法成为工具参数的复合 handler 参数改为写入 `ScanReport.warnings()`，
  不再静默丢弃。

- **`Optional<T>` 请求参数**按内层类型映射并导出为非必填，不再导出成 `string` 且 `required=true`。

### 修复

- **验证脚本不再有假绿路径。** PM 脚本先记下项目里的任务 id，再要求**本次新出现**的委托任务；
  审批脚本要求请求 1 在运行前是 `pending`；两者都改为有界轮询，不再固定睡一次。家族段门禁数原始
  `BEGIN` 标记，坏掉的区域不再能被旁边正常的区域盖住。CI 矩阵设 `fail-fast: false`，JDK 21 那条腿
  在 17 红时也会报结果。

- **Gradle 构建与 Maven pom 对齐。** `keelbase-tools-export` 补上对 `keelbase-client` 的依赖；
  `keelbase-spring-boot-autoconfigure` 在编译与测试类路径上补齐 actuator 与 `provided` 依赖；
  `keelbase-test-support` 成为 Gradle 模块；示例的契约测试补上 test-support 依赖；H2 与 therapi
  测试依赖补上，且 scribe 显式写成测试注解处理器。`./gradlew clean build` 通过。

- **Central 发布钉住 Maven 3.9.16**（已知的 3.10.0 / central-publishing-plugin 缺陷），并加入
  `workflow_dispatch`，失败的发布可以从 `main` 重跑而无需移动已公开的 tag。

## [0.1.8] - 2026-10-18

**English**

### Added

- **The skeleton ships a contract test by default.** A project generated by
  `keelbase-java-skeleton` now contains `{{Domain}}ContractTest` (extending
  `keelbase-test-support`'s `KeelbaseContractTest`), a test-support dependency, and a local sample
  secret in **test** resources — so `mvn test` guards the integration from day one (export contract /
  protected path 401 / delegated JWT 2xx), closing the loop with `ci-template.yml`. A generated
  project reports 3/3 green.

- **`querySideEffect` performs the reverse reconciliation.** `KeelbaseClient.querySideEffect`
  asks the main repository's `GET /api/v1/external/effects/{resultType}/{resultId}` with the service
  identity (`x-api-key`, `keelbase.client.side-effect-api-key`) and answers a `SideEffectStatus`: a
  local entity reports the truth of its own soft delete, `proxy_call` says the revoke state lives on
  the Java side, and a 404 is `found=false`. Five tests cover the shapes.

- **PM and Approval reference projects reach CRM parity.** Delegated write-back
  (`createdBy` / `decidedBy`), compensation tests, and domain-level e2e scripts
  (`verify-pm-e2e.mjs`, `verify-approval-e2e.mjs`) cover the full loop: export → confirmation gate →
  streamed approval → delegated identity → audit → revoke → compensation.

- **CI verifies Java 21 LTS** alongside the Java 17 runtime baseline (Maven job matrix); the Gradle
  job stays on 17.

### Fixed

- **The skeleton's controller paths match `revokePath`.** The class prefix
  `@RequestMapping("/api/items")` combined with method paths produced `/api/items/items` and
  `/api/items/compensation/…`, while `revokePath` declared `/api/compensation/items/{id}` — the
  delegated revoke answered 404. The prefix is now `/api`, so the real endpoints are `/api/items` and
  `/api/compensation/items/{id}`, and the contract test passes.

- **The scaffold no longer ships a public signing secret.** `application.yml` has no default for
  `keelbase.delegation.secret`; an unset variable fails fast instead of accepting tokens signed with a
  value published in this repository. The sample value lives in `src/test/resources` for the contract
  test only.

- **The verification scripts assert instead of warning.** A missing delegated identity and a
  compensation that did not change the Java row are failures, not `console.warn`, so a script cannot
  print "闭环通过" while the loop is broken. They also use the current seed credentials, overridable
  through environment variables.

**中文**

### 新增

- **脚手架默认带契约测试。** `keelbase-java-skeleton` 生成的项目现在包含 `{{Domain}}ContractTest`
  （继承 `keelbase-test-support` 的 `KeelbaseContractTest`）、test-support 依赖，以及放在**测试**
  资源里的本地样例密钥 —— 新项目从第一天 `mvn test` 就守护接入合规（导出契约 / 受保护路径 401 /
  委托 JWT 2xx），与 `ci-template.yml` 闭环。生成项目实测 3/3 绿。

- **`querySideEffect` 完成反向对账。** `KeelbaseClient.querySideEffect` 以服务身份（`x-api-key`、
  `keelbase.client.side-effect-api-key`）查询主仓的
  `GET /api/v1/external/effects/{resultType}/{resultId}`，答一个 `SideEffectStatus`：本地实体如实
  报告自己那一行是否软删，`proxy_call` 明说撤销态在 Java 侧，404 则是 `found=false`。五种形状各有测试。

- **PM 与 Approval 参考项目补齐到 CRM 的完整度。** 委托身份写回（`createdBy` / `decidedBy`）、
  补偿测试，以及域级 e2e 脚本（`verify-pm-e2e.mjs`、`verify-approval-e2e.mjs`）覆盖完整闭环：
  导出 → 确认门控 → 流式批准 → 委托身份 → 审计 → 撤销 → 补偿。

- **CI 增加 Java 21 LTS 验证**，与 Java 17 运行时基线并列（Maven job 矩阵）；Gradle job 保持 17。

### 修复

- **脚手架的控制器路径与 `revokePath` 一致了。** 类前缀 `@RequestMapping("/api/items")` 与方法路径
  叠加，真实端点成了 `/api/items/items` 与 `/api/items/compensation/…`，而 `revokePath` 声明的是
  `/api/compensation/items/{id}` —— 委托撤销因此回答 404。类前缀改为 `/api`，真实端点即
  `/api/items` 与 `/api/compensation/items/{id}`，契约测试通过。

- **脚手架不再带公开的签名密钥。** `application.yml` 不再为 `keelbase.delegation.secret` 提供默认值；
  未注入时快速失败，而不是接受用本仓公开值签出的令牌。样例值只存在于 `src/test/resources`，仅供契约测试。

- **验证脚本改为断言，不再只是警告。** 缺少委托身份写回、或补偿没有真的改动 Java 那一行，都是失败而不是
  `console.warn`，故脚本不可能在闭环已断时仍打印「闭环通过」。脚本同时改用当前种子账号，并可用环境变量覆盖。

## [0.1.7] - 2026-09-03

> 0.1.7 — Java 接入可观测与合规：actuator 健康指示器 / 治理台策略实时拉取 / CI 接入合规模板。

### Added

- **actuator HealthIndicator** — `/keelbase/status` 接入 Spring Boot actuator `/health`（`keelbase.health.enabled`，需消费者自加 `spring-boot-starter-actuator`）：healthy/degraded→`UP`（degraded 带 warnings）、error→`DOWN`；`KeelbaseHealthIndicator` 复用 `KeelbaseStatusController.status()` 健康判定，不重写规则。
- **治理策略拉取** — `KeelbasePolicyClient`（治理台 `GET /api/v1/external/governance/policy` + x-api-key 服务身份）拉取实时治理策略（工具 override / 审计粒度），业务系统本地按策略约束执行；复用 `keelbase.audit.base-url/api-key`；未配置 → `Optional.empty()`。`GovernancePolicy` record + autoconfigure 注册 bean。
- **CI 接入合规模板** — `ci-template.yml` + `docs/ci-integration.md`/`ci-integration.zh-CN.md`：消费方复制即得「契约测试 + 本地自检 + 导出门禁」双 job，接入合规持续守护。

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
