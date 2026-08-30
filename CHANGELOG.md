# Changelog

All notable changes to this project are documented in this file. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning follows [SemVer](https://semver.org/).

## [Unreleased]

### Added

- **`keelbase-client`** — `KeelbaseClient` (delegation-token lifecycle: `obtain` / `obtainAndCache` with proactive refresh / local `verify`, audience-bound) + `KeelbaseAuditReporter` (async audit reporting to the governance plane, D2-3a `/external/audit`, local-log fallback when unconfigured).
- **`keelbase-java-crm-example`** — Integrator Kit Reference Project: a legacy Java CRM (customers/orders/follow-ups) declared as 5 governed AI tools (read R1 / write R3 + revocable `create_followup_task`), the real Java side of the `external-crm-demo` CRM domain, with `verify-crm-e2e.mjs` and `docs/reference-project-crm{,.zh-CN}.md`.
- **Class-level `@KeelbaseTool`** — `@KeelbaseTool` now also targets `TYPE`: a whole `@RestController` is tooled up in one go (tool name = method `camelCase → snake_case`), with method-level `enabled=false` to exclude helper/internal endpoints. Method-level attributes override class-level. Demoed by `keelbase-java-crm-example`'s `CrmInsightsController` (3 tools + 1 excluded).
- **Export param description** — `@RequestParam` parameters now export enum allowed values (`可选: A/B/C`) and explicit defaults (`默认: x`) into the tool parameter `description`, matching the `@RequestBody` enum description so the LLM sees precise options.
- **Spring Boot 3.5.16** — dependency BOM + Gradle plugin upgraded from 3.2.5 to 3.5.16 (Spring Framework 6.2); full Maven + Gradle builds green.
- **Swagger/OpenAPI doc extraction** — when springdoc annotations are on the classpath, tool descriptions come from `@Operation(summary/description)`, parameter descriptions from `@Parameter(description)`, and `@RequestBody` field descriptions from `@Schema(description)` (reflection-based, no hard dependency), so `@KeelbaseTool`/`@Schema` descriptions need not be repeated.
- **crm-example springdoc demo** — the CRM sample annotates `CrmInsightsController` (class-level `@KeelbaseTool` + per-method `@Operation`) and `CrmController.listCustomers` (`@Parameter`), proving class-level tools and params pick up descriptions automatically; `CrmExportTest` asserts them.
- **crm-example pagination** — `list_customers` now paginates: `page`/`limit` params (with `@Parameter` descriptions + defaults exported, e.g. `页码（从 1 起）；默认: 1`) and a stable `{items,total,page,limit}` response; `CrmExportTest` asserts the pagination params.

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
