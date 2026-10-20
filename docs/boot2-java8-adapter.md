# Boot 2 / Java 8 Adapter — Legacy Java Stack Integration

Not every team runs Spring Boot 3 + Java 17. This page states exactly what the starter requires, which module uses which API, and the fallback for hosts that cannot upgrade: the **API proxy (B path)** in KeelBase.

Status labels used on this page:

- **Measured** — verified by reading the source/build files in this checkout, or by inspecting the class files under `target/classes`.
- **Derived** — inferred from source-level API usage (the JVM will reject the published bytecode before any of this matters).
- **Not tested** — no Java 8/11 JDK and no Spring Boot 2 runtime were available on the machine used for this page (JDK 17/25 only). No Maven/Gradle build was run for this change.

## 1. Short answer

1. The starter is a **Spring Boot 3 + Java 17** artifact. Every module is compiled to class-file major **61** (Java 17). Adding it to a Boot 2 or Java 8 application fails at class loading, before Spring starts.
2. The fallback is the **API proxy (B path)**: KeelBase reads an OpenAPI description of your existing REST API, registers governed proxy tools, and calls your endpoints with a short-lived delegated JWT. **The starter does not run in the legacy JVM.** Existing code and deployment stay as they are.
3. The B path is **not zero work**: you provide an OpenAPI description; you optionally add a delegated-JWT filter and a compensation endpoint. Section 5 is the migration checklist.
4. Governance split: KeelBase owns tool-level authorization, confirmation/approval, audit, and revoke orchestration; your endpoint owns data-level scoping and the business transaction.

## 2. Build baseline (measured)

| Constraint | Value | Evidence |
|---|---|---|
| Java source/target | 17 | `pom.xml:32-34` |
| Spring Boot BOM | 3.5.16 | `pom.xml:36`; `build.gradle:6,24` |
| Class-file version | 61 (Java 17) | inspected the first `*.class` in `target/classes` of every built module |
| Servlet namespace | `jakarta.*` | module POMs: `keelbase-delegation-filter/pom.xml:25`, `keelbase-tools-export/pom.xml:49`, `keelbase-compensation/pom.xml:33`, `keelbase-spring-boot-autoconfigure/pom.xml:55`, `keelbase-test-support/pom.xml:47` |
| Validation namespace | `jakarta.validation.*` | `keelbase-tools-export/pom.xml:41` |

The Maven build sets `maven.compiler.source` / `maven.compiler.target` to 17, not `--release 17`. The practical result is still Java 17 bytecode; the distinction only matters if you recompile a fork.

## 3. Path A — per-module support matrix

“Source floor” is the oldest Java language/API level the module's **own source** could compile against, ignoring the repository's 17 target. “Published” is the artifact this repository actually produces, which is always Java 17.

Line numbers were taken from the working tree at the time of writing. The stable reference is the **file plus the named symbol**; if the line moves after a later refactor, search for the symbol.

| Module | Hard dependency signals (file:line) | Source floor | Boot floor | On Boot 2 + Java 17 | On Java 8 / 11 |
|---|---|---|---|---|---|
| `keelbase-tools-annotation` | Annotations and an enum only; no Spring, no `jakarta`; `KeelbaseTool.java`, `KeelbaseRiskLevel.java`, `EnableKeelbaseTools.java` | Java 8 | none | Loadable, but gives no integration by itself | **No** — published class file is 61 |
| `keelbase-delegation-filter` | `jakarta.servlet.*` (`DelegationAuthFilter.java:11-14`); `record DelegationPrincipal` (`DelegationPrincipal.java:15`); `String.isBlank()` (`DelegationAuthFilter.java:59,70,83,110`; `DelegationPrincipal.java:24`) | 16 | Boot 3 (Jakarta) | **No** — `jakarta` + record; Boot 2 uses `javax` | **No** |
| `keelbase-tools-export` | `jakarta.servlet.*` (`ProxyToolsScanner.java:48-52`); `jakarta.validation.*` (`RequestBodyFields.java:8-10`); records (`ProxyToolItem.java:13`, `ProxyToolsConfig.java:8`, `ToolParameter.java:6`, `ProxyToolsScanner.java:83`); `List.of` / `List.copyOf` / `Set.of` (`ProxyToolsScanner.java:45-46,86,122`; `KeelbaseStatusController.java:80,104,139`); `instanceof` pattern (`JavadocDocExtractor.java:128,133`); switch expression (`KeelbaseToolsCli.java:28,37`); `java.net.http` (`ProxyToolsExportClient.java:10-12`); Spring handler API (`ProxyToolsScanner.java:17,19,95`) | 16 | Boot 3 | **No** | **No** |
| `keelbase-compensation` | `jakarta.servlet.http.HttpServletRequest` (`KeelBaseCompensationSupport.java:7`); `Map.of` (`:57,61,65,69,75`); imports delegation-filter records (`:5-6`) | 16 (transitive) | Boot 3 | **No** | **No** |
| `keelbase-client` | Records (`GovernancePolicy.java:18,24`; `KeelbaseAuditEvent.java:11`; `KeelbaseTokenIssue.java:14`; `SideEffectStatus.java:23`); `java.net.http` (`KeelbaseClient.java:19-21`; `KeelbaseAuditReporter.java:12-14`); `Map.of` (`GovernancePolicy.java:60`); `String.isBlank()` (`KeelbaseClient.java:69,83,154,215`); `@ConfigurationProperties` JavaBean (`KeelbaseClientProperties.java:23`); POM depends on delegation-filter (`keelbase-client/pom.xml:18`) | 16 | Spring Boot annotation only; no servlet/MVC in its own sources | **Not certified** — sources are largely framework-neutral, but the artifact is 17 and the POM pulls a `jakarta` module | **No** |
| `keelbase-spring-boot-autoconfigure` | `jakarta.servlet.*` (`SecurityDelegationWriter.java:7-10`); `@AutoConfiguration` (`CompensationAutoConfiguration.java:36`; `DelegationAutoConfiguration.java:38`); `AutoConfiguration.imports` resource; Spring handler API (`ToolsExportAutoConfiguration.java:21,36,38,45`); `List.of` (`KeelbaseHealthIndicator.java:42,44,45`); `var` (`ToolsExportValidator.java:29`); `instanceof` pattern (`SecurityDelegationWriter.java:33`) | 16 | Boot 3 | **No** | **No** |
| `keelbase-spring-boot-starter` | Aggregator POM only | Inherits | Boot 3 | **No** | **No** |
| `keelbase-maven-plugin` | Own source has no records/`jakarta`; POM depends on `keelbase-tools-export` (`keelbase-maven-plugin/pom.xml:34`) | 8 (own source) | Maven; runtime 17 due to the dependency | **Not useful** — needs a Java 17 runtime | **No** |
| `keelbase-test-support` | `jakarta.servlet-api` (`keelbase-test-support/pom.xml:47`); Spring Boot test | 17 | Boot 3 | **No** | **No** |
| Examples: `java`, `crm`, `pm`, `approval` | Records (`CrmCustomer.java:6`; `PmProject.java:8`; `PmTask.java:6`; `ApprovalRequest.java:6`; `FollowupRequest.java:8`); `Stream.toList()` (`CrmController.java:59,65,91`; `CrmInsightsController.java:44,50`); Boot 3 web | 17 | Boot 3 | **No** | **No** |

Notes that prevent common misreadings:

- `javax.crypto.SecretKey` appears in `keelbase-delegation-filter`, `keelbase-client`, and `keelbase-test-support`. It is a **JDK** package (available since Java 8), not Jakarta EE. It does not indicate Boot 2 compatibility. The Boot 2 blockers are `jakarta.servlet.*` and `jakarta.validation.*`.
- All `@ConfigurationProperties` classes in this repository use the **JavaBean** form (getters/setters), not constructor binding. That style also works on Boot 2.7; it is not the blocker.
- `Stream.toList()` (Java 16) is used in the CRM example, not in production modules. Production modules use `Collectors.toList()`, but they have other blockers (records, `jakarta.*`, `java.net.http`).
- The `keelbase-spring-boot-autoconfigure` module uses `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Boot 2.7 introduced that mechanism; older Boot 2.x used `spring.factories`. The mechanism is not the blocker — `jakarta.servlet` and Java 17 bytecode are.

### What each host can run

| Host | Path A (starter) | Path B (API proxy) |
|---|---|---|
| Spring Boot 3 + Java 17 | Full — all modules | Yes |
| Spring Boot 2.7 + Java 17 | Only `keelbase-tools-annotation` loads; `keelbase-client` is not certified; no full capability | Yes |
| Spring Boot 2 + Java 11 | No module loads (class-file major 61) | Yes |
| Spring Boot 2 + Java 8 | No module loads. `keelbase-tools-annotation` source is Java 8-compatible if recompiled, but it provides no integration by itself | Yes |
| Spring Boot 3 + Java 8 | Impossible — Boot 3 requires Java 17 | Not applicable |

## 4. Path B — the API proxy fallback

### 4.1 What it is

The **B path** is KeelBase's API-proxy path in the AI Bridge specification. OpenAPI operations become governed proxy tools; at execution time KeelBase calls your existing REST endpoint and injects a delegated JWT.

```text
OpenAPI description
  -> ai_proxy_tools (KeelBase Settings)
  -> ProxyToolRegistryService
  -> ProxyTool.execute
  -> HTTP {method} {baseUrl}{path}
       Authorization: Bearer <delegated JWT>
  -> your existing endpoint
```

References in the KeelBase main repository:

- `docs/manual/ai-bridge.md` §4 (B path) and §5 (delegation token).
- `Server-NestJS/src/ai/proxy/proxy-tool.ts` (runtime proxy tool).
- `Server-NestJS/src/ai/proxy/proxy-revoker.service.ts` (revoke orchestration).

The legacy application does not embed the starter and does not need a KeelBase SDK.

### 4.2 What the legacy side must provide

| Need | Required? | Notes |
|---|---|---|
| Existing REST endpoints | Yes | Your current API stays as-is |
| OpenAPI 3 / Swagger 2 description | Yes | springdoc or springfox on Boot 2, or a hand-written spec |
| Delegated JWT verification | Recommended | HS256, `aud` / `iss` / `exp`; on Java 8 implement a `javax.servlet.Filter` or verify per endpoint |
| Compensation endpoint | Only for revoke | Idempotent, returns 2xx |
| Network reachability from KeelBase | Yes | `baseUrl` |
| Data-level scoping | Yes | Filter by the delegated identity in your endpoint |

### 4.3 Connect (commands verified in the main repository)

Run these in the **KeelBase main repository**, not in the starter:

```bash
node scripts/keelbase-init.mjs --import-openapi-proxy ./legacy-openapi.yaml \
  --base-url http://legacy-crm:8080/api \
  --audience legacy-crm \
  --out proxy-config.json
```

Then write the generated configuration to KeelBase:

```text
PUT /api/v1/settings/ai_proxy_tools   (admin token; hot reload, no restart)
```

or paste the JSON into the admin Settings page.

What was verified:

- Flags `--import-openapi-proxy`, `--base-url`, `--audience`, `--out`: `scripts/keelbase-init.mjs:64,76-78`.
- Conversion logic: `scripts/generator/import-openapi-proxy.mjs`.
- `PUT /api/v1/settings/ai_proxy_tools`: `keelbase-tools-export/src/main/java/cn/com/keelbase/export/ProxyToolsExportClient.java:78-100`.
- Hot reload: `Server-NestJS/src/settings/settings.service.ts:26,42` (an `ai_proxy_tools` change listener), so no restart is required.
- Runnable demo: `scripts/demo-external-crm.sh`; `docs/manual/external-crm-demo.md`; `specs/external-crm.openapi.json`.

### 4.4 Minimal OpenAPI fragment (configuration only)

```yaml
paths:
  /customers:
    get:
      operationId: listCustomers
      summary: List customers
      responses:
        "200":
          description: OK
  /followups:
    post:
      operationId: createFollowup
      summary: Create a follow-up task
      x-keelbase-risk-level: R3
      x-keelbase-revoke-path: "DELETE /followups/{id}"
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                customerId: { type: integer }
                content: { type: string }
      responses:
        "200":
          description: OK
```

Generator mapping (verified in `scripts/generator/import-openapi-proxy.mjs:14,161-178`):

- `operationId` camelCase → snake_case; fallback `{method}_{path}`.
- GET → R1; POST/PUT/PATCH/DELETE → R3.
- `x-keelbase-risk-level` overrides the default (R0-R5).
- `x-keelbase-revoke-path` becomes the tool's `revokePath`.
- Path templates `{param}` are passed through; header parameters are skipped.

### 4.5 Governance split

| Concern | Where it lives |
|---|---|
| Tool-level authorization (roles / enabled) | KeelBase policy |
| Risk gating (R1 auto / R3 confirm / R4 approval / R5 block) | KeelBase |
| Confirmation and approval state | KeelBase |
| Audit (operation audit, decision evidence, hash chain) | KeelBase |
| Delegated JWT issuance and injection | KeelBase (`DelegationTokenService`) |
| Revoke orchestration | KeelBase (`ProxyToolRevokerService`) |
| Data-level row / tenant scoping | **Your endpoint**, using the delegated identity |
| The business transaction itself | **Your endpoint** |

Honest boundary: KeelBase proxy tools carry a tool-level `riskLevel` and an `audience`. They do **not** inject row-level predicates into your SQL. If the AI must see only the caller's rows, your endpoint must filter by the delegated subject. This is the same responsibility the starter helps you implement with `@DelegationUser` + `KeelBaseUserMapper`; the starter does not enforce it for you either.

### 4.6 Delegation contract for a hand-written filter

The contract below is what the starter's filter verifies (`keelbase-delegation-filter/src/main/java/cn/com/keelbase/delegation/DelegationAuthFilter.java`). A Java 8 implementation must reproduce it with `javax.servlet` and any HS256-capable JWT library.

- Header: `Authorization: Bearer <token>`.
- Algorithm: HS256; shared secret `DELEGATION_SECRET` (falls back to `JWT_SECRET` if unset; production should set it explicitly). Minimum 32 bytes.
- Claims: `sub` = `oidcSub` or `local:<userId>`; `oidcSub` when present; `aud` = your system's audience; `iss` = `keelbase`; `exp` = short-lived (default 300s, configurable 60-3600s).
- Verify signature, `aud`, optional `iss`, and expiry. Fail closed on protected paths.

Evidence: `Server-NestJS/src/auth/delegation-token.service.ts:56-79`; starter filter as above.

**Caveat**: the standalone Java reference in the KeelBase main repository (`docs/integrator-kit/java-compensation-example.md`) uses `Map.of` (Java 9+). On Java 8, replace it with `Collections.singletonMap` / a `HashMap`. That example was not compiled or run against Java 8 for this page.

### 4.7 Revoke contract

- `revokePath` format: `DELETE /path/{id}` or `POST /path/{id}/cancel`; the method is optional and defaults to `POST`.
- Placeholders `{...}` are replaced with the side-effect `resultId`.
- KeelBase calls the compensation endpoint with a delegated JWT.
- The endpoint should be idempotent and return 2xx; a non-2xx response is surfaced as a revoke failure.
- `revokeClass` is derived: write + `revokePath` → `governed_external`; read, or write without `revokePath` → `none`.
- Without `revokePath`, KeelBase returns an explicit “external side effect requires Java-side compensation” result; it does not pretend the revoke succeeded.

Evidence: `Server-NestJS/src/ai/proxy/proxy-revoker.service.ts`; `Server-NestJS/src/ai/proxy/proxy-tool.ts` (`revokeClass` derivation).

### 4.8 What you do not get on the B path

| Starter capability | On the B path |
|---|---|
| `@KeelbaseTool` declaration | You write an OpenAPI description |
| `GET /keelbase/proxy-tools/export` | Not available; generate the config from OpenAPI |
| `GET /keelbase/status` integration diagnostics | Not available; use KeelBase-side diagnostics or your own health check |
| `DelegationAuthFilter` + `@DelegationUser` + `KeelBaseUserMapper` | Hand-write a `javax.servlet.Filter` (Java 8) or verify per endpoint |
| `SecurityDelegationWriter` (Spring Security bridge) | Hand-write |
| `KeelBaseCompensationSupport` (idempotent scaffold) | Hand-write the compensation endpoint |
| `KeelbaseClient` (outbound token issuance, audit reporting) | Call the KeelBase HTTP API yourself (`/api/v1/auth/delegation-token`, audit endpoint) |
| `KeelbaseContractTest` | No contract-test harness; use your own tests |
| Auto-configuration and fail-fast startup checks | No auto-config; validate configuration yourself |
| Zero-boilerplate Javadoc inference (`infer = true`) | No; descriptions come from OpenAPI `summary` / `description` |

What you keep: R1 auto / R3 confirmation / R4 approval / R5 block, the audit chain, delegated identity, revoke via `revokePath`, and the tool list in KeelBase.

## 5. Migration checklist: starter to B path

1. Do **not** add `keelbase-spring-boot-starter` to the legacy application. Keep the current Boot 2 / Java 8 deployment.
2. Inventory the endpoints the AI should use. Prefer a small, explicit set over “everything”.
3. Write or export an OpenAPI 3 / Swagger 2 description. On Boot 2, springdoc or springfox can generate it; a hand-written spec is also valid.
4. For each operation, set `operationId`, `summary`, path/query/body parameters, and the expected risk level. Writes default to R3; use `x-keelbase-risk-level` for R4/R5.
5. For writes that can be undone, add `x-keelbase-revoke-path` and implement the compensation endpoint (idempotent, 2xx).
6. Generate the proxy configuration with `node scripts/keelbase-init.mjs --import-openapi-proxy ...`.
7. Review the generator's `skipped`, `riskBases`, and `available` output before applying it.
8. Set the top-level `audience` to the legacy system identifier; configure the same `DELEGATION_SECRET` on both sides.
9. Implement delegated-JWT verification on the legacy side. On Java 8, use a `javax.servlet.Filter` plus an HS256 JWT library; verify `aud`, `iss`, and `exp`.
10. Enforce data-level scoping in each endpoint using the delegated identity.
11. Apply the config with `PUT /api/v1/settings/ai_proxy_tools` (hot reload) or the admin Settings page.
12. Verify end to end: a read is auto-executed (R1), a write asks for confirmation (R3), the audit record is visible, a revoke calls the compensation endpoint, and another user's data is rejected (403).
13. Remove starter-specific expectations: there is no `/keelbase/status` and no `/keelbase/proxy-tools/export` on the legacy side.

## 6. Scope, evidence, and what was not tested

### Measured

- Build properties: `pom.xml:32-36`; `build.gradle:6,24`.
- Class-file version 61 for every built module (inspected `target/classes/**/*.class`).
- Per-module API usage: the file:line references in Section 3.
- Main-repository commands and paths: `scripts/keelbase-init.mjs`, `scripts/generator/import-openapi-proxy.mjs`, `scripts/demo-external-crm.sh`, `specs/external-crm.openapi.json`, `docs/manual/external-crm-demo.md`, `docs/integrator-kit/java-compensation-example.md`.
- Main-repository endpoints and config keys: `PUT /api/v1/settings/ai_proxy_tools`, `POST /api/v1/auth/delegation-token`, `ai_proxy_tools`, `x-keelbase-risk-level`, `x-keelbase-revoke-path`.

### Not tested

- No Java 8 or Java 11 JDK on the machine used for this page (JDK 17/25 only).
- No Spring Boot 2 runtime.
- No Maven/Gradle build was run for this change; the checkout is shared with another session that is building. This change is documentation-only.
- Therefore the compatibility matrix is a **static dependency analysis**, not a runtime certification. If you need a guarantee, validate the B path against a Boot 2 / Java 8 sample application in CI before relying on it.

### Corrections in this revision

The previous draft of this page (0.1.8) contained the following claims that do not match the code:

1. “`keelbase-delegation-filter` only depends on Spring Web, so Boot 2 can compile it.” It imports `jakarta.servlet.*` and declares a `record`; it cannot run on Boot 2 / Java 8. The Java 8 path is a hand-written `javax.servlet.Filter`, not this module.
2. “Restart KeelBase after writing `ai_proxy_tools`.” The settings service fires a change listener; the configuration is hot-reloaded. No restart.
3. “The reference is `external-crm-demo`.” That is not a repository path. The real entry points are `scripts/demo-external-crm.sh`, `docs/manual/external-crm-demo.md`, and `specs/external-crm.openapi.json`.
4. “The B path is zero-code.” Overstated. The legacy side still needs an OpenAPI description, and optionally a JWT filter and a compensation endpoint.
5. “The scanner requires Spring 6.1 / Boot 3.2+.” The code calls `getPathPatternsCondition()` and falls back to `getPatternsCondition()`; the hard evidence is Spring 6 `RequestMappingHandlerMapping` + Jakarta + Java 17. The exact 6.0/6.1 boundary was not measured.

## 7. See also

Starter documentation:

- [Development Guide](development-guide.md)
- [Delegated Identity](delegated-identity.md)
- [Compensation](compensation.md)
- [Troubleshooting](troubleshooting.md)

KeelBase main repository:

- [AI Bridge specification](https://github.com/rain6fish/KeelBase/blob/main/docs/manual/ai-bridge.md)
- [Standalone Java compensation example](https://github.com/rain6fish/KeelBase/blob/main/docs/integrator-kit/java-compensation-example.md)
- [External CRM demo](https://github.com/rain6fish/KeelBase/blob/main/docs/manual/external-crm-demo.md)
