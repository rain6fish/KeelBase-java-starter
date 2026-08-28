# Contributing to KeelBase-java-starter

Thank you for your interest in contributing! This project is the Java/Spring Boot bridge for [KeelBase](https://github.com/rain6fish/KeelBase) — it connects existing Java systems to KeelBase as governed AI tools. We welcome contributions of all kinds: bug fixes, tests, docs, and improvements.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Pull Request Process](#pull-request-process)
- [AI-Generated Code Policy](#ai-generated-code-policy)
- [Intellectual Property](#intellectual-property)
- [Reporting Issues](#reporting-issues)

---

## Code of Conduct

This project follows a [Code of Conduct](./CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

---

## Getting Started

### Prerequisites

- **JDK 17+** (Temurin/OpenJDK recommended)
- **Maven 3.9+**
- A running [KeelBase](https://github.com/rain6fish/KeelBase) backend (optional, for the end-to-end verification script)

### Local Setup

```bash
git clone https://github.com/rain6fish/KeelBase-java-starter.git
cd KeelBase-java-starter
mvn install          # compiles all modules + runs the JUnit suite
```

> The compiler requires `-parameters` (already configured in the parent `pom.xml`) because `@PathVariable` / `@RequestParam` names are resolved via reflection.

---

## Development Workflow

### Module layout

| Module | Responsibility |
|---|---|
| `keelbase-tools-annotation` | `@KeelbaseTool`, `KeelbaseRiskLevel` |
| `keelbase-delegation-filter` | Delegation JWT verification + user-mapping SPI |
| `keelbase-tools-export` | Annotation scanner + `ai_proxy_tools` export |
| `keelbase-compensation` | Compensation scaffold + idempotency ledger |
| `keelbase-spring-boot-autoconfigure` | Auto-configuration wiring |
| `keelbase-java-example` | Reference app (not published) |

### Contract alignment (important)

This starter is a **contract-level bridge** to KeelBase. Anything serialized across the wire must match KeelBase exactly:

- **Delegation JWT**: `{sub, oidcSub?, aud, iss:'keelbase', iat, exp}`, HS256, `DELEGATION_SECRET` (≥32 bytes).
- **`ai_proxy_tools`**: `{baseUrl, audience, tools:[{name, description, method, path, parameters, queryParams, riskLevel, revokePath}]}`; read GET=R1 / write POST·PUT·PATCH·DELETE=R3; complex types → `string`.
- **`baseUrl` is the server root**; tool `path` is the full path (KeelBase's ProxyTool concatenates `baseUrl + path`).
- **Compensation**: revocation endpoints must be idempotent and return 2xx.

When you change any serialized shape, update the KeelBase side (generator / `proxy-tool.ts` / `proxy-revoker.service.ts`) in lockstep, or the bridge breaks silently.

### Build & test

```bash
mvn install                 # full build + JUnit
mvn install -pl keelbase-java-example -am   # example only
node scripts/verify-java-starter-e2e.mjs --verify --llm   # e2e (needs KeelBase + example running)
```

---

## Pull Request Process

1. Fork the repo and create a branch from `main`.
2. Make your change with tests. New behavior must come with JUnit coverage.
3. Run `mvn install` and make sure the whole reactor is green.
4. If you changed a wire contract, verify against KeelBase (see [Contract alignment](#contract-alignment)).
5. Open a PR with a clear description of what and why. Keep the diff focused.

---

## AI-Generated Code Policy

This project is itself part of the KeelBase AI ecosystem, so AI-assisted development is welcome — but with guardrails:

- **Review everything.** AI-generated code must be read, understood, and tested by a human before it lands. Never merge code you cannot explain.
- **Tests are mandatory.** AI output that changes behavior must come with tests proving the behavior.
- **Be honest about provenance.** Mention AI assistance in the PR description when it materially shaped the change.
- **No security-critical blind spots.** Delegation verification, idempotency, and audit paths deserve extra scrutiny — these are the trust boundary of the bridge.

---

## Intellectual Property

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](./LICENSE). If your contribution includes code you did not write yourself, ensure you have the right to contribute it under this license.

---

## Reporting Issues

- **Bugs / features**: open a [GitHub Issue](https://github.com/rain6fish/KeelBase-java-starter/issues) with a minimal repro.
- **Security vulnerabilities**: do **not** open a public issue — see [SECURITY.md](./SECURITY.md) for private disclosure.
