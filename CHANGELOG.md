# Changelog

All notable changes to this project are documented in this file. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning follows [SemVer](https://semver.org/).

## [Unreleased]

### Added

- **`keelbase-tools-annotation`** — `@KeelbaseTool`, `KeelbaseRiskLevel` (R0-R5), `@EnableKeelbaseTools`.
- **`keelbase-delegation-filter`** — `DelegationAuthFilter` (HS256 + aud/iss/exp verification; fail-open with protected-path fail-closed), `@DelegationUser` argument injection, `KeelBaseUserMapper` SPI, optional Spring Security adapter.
- **`keelbase-tools-export`** — annotation scanner + type mapping aligned with the KeelBase generator, `GET /keelbase/proxy-tools/export`.
- **`keelbase-compensation`** — `KeelBaseCompensationSupport` scaffold, `RevocationLedger` idempotency ledger, `CompensationAuditSink`.
- **`keelbase-spring-boot-autoconfigure` / `keelbase-spring-boot-starter`** — auto-configuration and aggregator modules.
- **`keelbase-java-example`** — reference Spring Boot app (read R1 / write R3 with revokePath / compensation endpoint).
- **E2E verification** — `scripts/verify-java-starter-e2e.mjs` (configure + verify), validated against a real KeelBase: confirmation gate → streaming approve → proxy write with delegated identity → audit → revoke → compensation.

### Fixed

- Verification script must **approve while streaming** — the confirmation gate suspends the SSE stream until a decision, so awaiting the full stream first lets the token expire (404).
- `baseUrl` convention is the **server root** + full tool `path` (avoids a doubled `/api` prefix).

## [0.1.0-SNAPSHOT]

Initial development snapshot. Not yet released to Maven Central.
