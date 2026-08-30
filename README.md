# KeelBase Java Starter

> **Give legacy Java/Spring systems governed AI capabilities.** A Spring Boot starter that connects your existing systems to [KeelBase](https://github.com/rain6fish/KeelBase) as governed AI tools — delegated identity, proxy-tool export, and compensation endpoints. Apache-2.0.

> KeelBase is an open-source **Enterprise AI Trust Runtime**. With this starter, your existing Java/Spring REST endpoints become AI tools: the AI operates your real data under KeelBase governance — row-level permissions, human confirmation for writes, tamper-evident audit chains, and revocable side effects.

<p align="center">
  <img src="docs/keelbase-java-starter-position.svg" alt="KeelBase Java Starter — positioned between the KeelBase Trust Runtime and your Java business systems" width="840">
</p>

## What it does

1. **Delegated identity** (`DelegationAuthFilter`): verifies the delegation JWT that KeelBase attaches to forwarded calls (HS256 + audience + issuer + expiry), maps it to a local user, and injects `@DelegationUser DelegationPrincipal`. Works without Spring Security; auto-writes the Spring Security context when Security is on the classpath. `secret` and `audience` are required — the app fails fast at startup if either is missing.
2. **Tool declaration + export** (`@KeelbaseTool`): annotate your `@RestController` methods, and `GET /keelbase/proxy-tools/export` produces the `ai_proxy_tools` config — write it into KeelBase Settings to register the tools. Types / risk levels / parameters align with the KeelBase generator. Jackson-aware parameter extraction (inheritance, `@JsonIgnore`, records) and a single `audience` source of truth (tools falls back to delegation).
3. **Compensation scaffold** (`KeelBaseCompensationSupport`): revocation endpoints for AI write side effects — delegated identity, idempotency, and audit out of the box.
4. **Diagnostics** (`GET /keelbase/status`): delegation config, resolved export audience, tool count, and configuration warnings — without ever leaking the secret.

## Documentation

| Topic | Description |
|---|---|
| [quickstart](docs/quickstart.md) | 10-minute end-to-end wiring guide |
| [configuration](docs/configuration.md) | Full property reference + audience resolution rules |
| [delegated-identity](docs/delegated-identity.md) | JWT, verification, user mapping, Spring Security, row-level ownership |
| [tool-declaration](docs/tool-declaration.md) | Annotation, parameter extraction, type mapping, risk levels |
| [compensation](docs/compensation.md) | Revocation call contract, idempotency, audit, multi-instance |
| [client](docs/client.md) | Delegation-token lifecycle (`KeelbaseClient`) + audit reporting |
| [reference-project-crm](docs/reference-project-crm.md) | Integrator Kit Reference Project: legacy Java CRM → AI CRM (real Java side) |
| [tool-patterns](docs/tool-patterns.md) | Recipes: pagination, filters, enum params, springdoc descriptions, class-level tools, write+revoke |
| [gradle-usage](docs/gradle-usage.md) | Consumer-side Gradle guide: dependency, config, versions |
| [troubleshooting](docs/troubleshooting.md) | Error codes, common mistakes, verification checklist |
| [production-checklist](docs/production-checklist.md) | Hardening, secret rotation, ops monitoring before go-live |

Chinese versions live alongside each file (`*.zh-CN.md`).

## Quick start (example)

```bash
# 1. Run the example (default 8081)
cd keelbase-java-example
mvn spring-boot:run

# 2. Diagnose the wiring (delegation config, resolved audience, tool count, warnings)
curl http://localhost:8081/keelbase/status

# 3. Export the ai_proxy_tools config
curl http://localhost:8081/keelbase/proxy-tools/export

# 4. Write it into KeelBase (PUT /settings/ai_proxy_tools, value = the exported JSON as a string), restart KeelBase
```

Published to Maven Central — add `cn.com.keelbase:keelbase-spring-boot-starter:0.1.1` as a dependency (0.1.0 and 0.1.1 are live). For the development snapshot (`0.1.2-SNAPSHOT`), install locally once with `mvn install`. Release automation (the `-Prelease` profile + Central Portal token flow) is configured — see [docs/release-central.md](docs/release-central.md). Full steps in [docs/quickstart.md](docs/quickstart.md).

## Configuration (application.yml)

```yaml
keelbase:
  delegation:
    secret: ${KEELBASE_DELEGATION_SECRET:}          # required; same as KeelBase DELEGATION_SECRET (>= 32 bytes)
    audience: legacy-crm                             # required; must equal the ai_proxy_tools top-level audience
    issuer: keelbase                                 # optional
    paths:                                           # protected paths (rejected when no Authorization header)
      - /api/compensation
  tools:
    base-url: http://localhost:8081                  # target server root (export; baseUrl + full path convention)
    # audience: legacy-crm                           # optional — falls back to delegation.audience
    # export-enabled: true                           # disable the export endpoint after registration
    # status-enabled: true                           # disable the /keelbase/status endpoint in lockdown
  compensation:
    ledger-size: 1024                                # idempotency ledger LRU cap
```

## Annotation example

```java
@RestController
@RequestMapping("/api")
public class FollowupController extends KeelBaseCompensationSupport<Map<String, Object>> {

    @GetMapping("/followups")
    @KeelbaseTool(name = "list_followups", description = "List follow-up tasks (read, R1 auto)")
    public List<Map<String, Object>> list() { ... }

    @PostMapping("/followups")
    @KeelbaseTool(name = "create_followup", description = "Create a follow-up task (write, R3 needs confirmation)",
                  revokePath = "DELETE /api/compensation/followups/{id}")
    public Map<String, Object> create(@RequestBody FollowupRequest req,
                                      @DelegationUser DelegationPrincipal principal) { ... }

    @DeleteMapping("/compensation/followups/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
        return handleRevoke(request, id, store::get,
                (item, subject) -> item.put("cancelled", true), "compensation.followups.revoke");
    }
}
```

## Modules

| Module | Purpose |
|---|---|
| `keelbase-tools-annotation` | `@KeelbaseTool` / `KeelbaseRiskLevel` / `@EnableKeelbaseTools` |
| `keelbase-delegation-filter` | Delegation verification filter + user-mapping SPI + `@DelegationUser` |
| `keelbase-tools-export` | Annotation scanner + type mapping + `/keelbase/proxy-tools/export` |
| `keelbase-compensation` | Compensation scaffold + idempotency ledger + audit hook |
| `keelbase-client` | `KeelbaseClient` (delegation-token lifecycle) + audit reporting to the governance plane |
| `keelbase-spring-boot-autoconfigure` | Auto-configuration (all four pieces + optional Security adapter) |
| `keelbase-java-crm-example` | Reference Project: legacy Java CRM → AI CRM (not published) |
| `keelbase-spring-boot-starter` | Aggregator (the only dependency you need) |
| `keelbase-java-example` | Example app (not published) |

## Contract (aligned with KeelBase)

- Delegation JWT: `{sub, oidcSub?, aud, iss:'keelbase', iat, exp}`, HS256, `DELEGATION_SECRET`.
- `ai_proxy_tools`: `{baseUrl, audience, tools:[{name, description, method, path, parameters, queryParams, riskLevel, revokePath}]}`; read GET=R1 / write POST·PUT·PATCH·DELETE=R3; types integer/number/boolean/string (complex → string).
- `baseUrl` is the **server root**; tool `path` is the **full path** (e.g. `/api/followups`). KeelBase's ProxyTool concatenates `baseUrl + path`.
- Revocation: `proxy-revoker` calls the compensation endpoint (with delegated identity); it must return 2xx and be idempotent.

## Build & test

Either build system works (Maven is canonical for CI/release; Gradle is a parallel dev/publish path — dependencies mirror the poms):

```bash
mvn install          # Maven: compiles all modules + runs JUnit
./gradlew build      # Gradle (wrapper included): same reactor, all tests
```

## E2E verification

```bash
# with KeelBase (localhost:3000) and the example (localhost:8081) running:
node scripts/verify-java-starter-e2e.mjs --configure   # export + write ai_proxy_tools (then restart KeelBase)
node scripts/verify-java-starter-e2e.mjs --verify --llm # full loop: confirmation gate -> streaming approve -> proxy write -> audit -> revoke -> compensation
```

## License

Apache-2.0
