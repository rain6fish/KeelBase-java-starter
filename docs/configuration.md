# Configuration Reference

All properties live under the `keelbase.*` prefix. Spring Boot's [relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html) applies — the same value can come from `application.yml`, environment variables, or `--key=value` CLI args.

| Property | Type | Default | Required | Description |
|---|---|---|---|---|
| `keelbase.delegation.enabled` | boolean | `true` | — | Master switch for the delegation filter. `false` disables verification (no `DelegationAuthFilter` bean). |
| `keelbase.delegation.secret` | string | — | **yes** | HS256 secret shared with KeelBase `DELEGATION_SECRET` (≥ 32 bytes). App **fails fast at startup** if missing. Env: `KEELBASE_DELEGATION_SECRET`. |
| `keelbase.delegation.audience` | string | — | **yes** | Target-system identifier. Must equal the top-level `audience` of the `ai_proxy_tools` config so KeelBase-forwarded calls verify. App **fails fast at startup** if missing. |
| `keelbase.delegation.issuer` | string | `keelbase` | — | Expected `iss` claim. Set blank to skip issuer validation. |
| `keelbase.delegation.paths` | list[string] | `[]` | — | Protected path prefixes (e.g. `/api/compensation`). Requests **without** an `Authorization` header matching these paths are rejected with 401 (fail-closed). Paths not listed are fail-open. |
| `keelbase.tools.enabled` | boolean | `true` | — | Master switch for the tools-export + status endpoints. |
| `keelbase.tools.base-url` | string | — | *yes for export* | Server root the tools are reachable at (e.g. `http://legacy-crm:8081`). Trailing slashes are stripped on export. Missing → export returns 500 with a clear message. |
| `keelbase.tools.audience` | string | — | — | Optional override of the exported audience. **Falls back to `keelbase.delegation.audience`** — you normally only set `delegation.audience`. If you set both, they must match (a mismatch is reported by `/keelbase/status` and breaks delegated verification). |
| `keelbase.tools.export-enabled` | boolean | `true` | — | Enables `GET /keelbase/proxy-tools/export`. Turn off in production once registered. |
| `keelbase.tools.status-enabled` | boolean | `true` | — | Enables `GET /keelbase/status` (diagnostics; never leaks the secret). |
| `keelbase.tools.strict` | boolean | `false` | — | Startup fail-fast: when `true`, an invalid `@KeelbaseTool` declaration (unresolvable method/path, illegal tool name) makes the app **fail to start** listing every skipped one — instead of a WARN-only skip that only shows up as a missing tool at export. |
| `keelbase.health.enabled` | boolean | `true` | — | Exposes `/keelbase/status` through the Spring Boot actuator `/health` (consumer must add `spring-boot-starter-actuator` for it to assemble): healthy/degraded → `UP` (degraded carries warnings), error → `DOWN` (carries errors), `status-enabled=false` → `UP`. |
| `keelbase.compensation.ledger-size` | integer | `1024` | — | LRU cap of the in-memory idempotency ledger for compensation endpoints. |
| `keelbase.client.base-url` | string | — | — | KeelBase service root (e.g. `http://localhost:3000`) for `POST /api/v1/auth/delegation-token`. Unset → `KeelbaseClient.obtain` disabled (only local `verify` works). See [client](client.md). |
| `keelbase.client.audience` | string | — | — | Target audience for delegation tokens. **Falls back to `keelbase.delegation.audience`.** |
| `keelbase.client.connect-timeout` / `read-timeout` | duration | `3s` / `10s` | — | HTTP timeouts for the delegation-token call. |
| `keelbase.audit.base-url` | string | — | — | Governance control-plane root (e.g. `http://localhost:3001`) for audit reporting (D2-3a `/external/audit`). Unset → reporting disabled (local log only). |
| `keelbase.audit.api-key` | string | — | — | Governance service identity (`GOVERNANCE_API_KEY`), sent as `x-api-key`. |
| `keelbase.audit.enabled` | boolean | `true` | — | Master switch for audit reporting; still disabled while `base-url` is unset. |

## Environment variables

Spring Boot relaxed binding means `keelbase.delegation.secret` can be injected as `KEELBASE_DELEGATION_SECRET`. For lists, use index-based names (e.g. `KEELBASE_DELEGATION_PATHS_0=/api/compensation`). Recommended production practice — keep the shared secret out of `application.yml`:

```yaml
keelbase:
  delegation:
    secret: ${KEELBASE_DELEGATION_SECRET}
```

## Audience resolution rules

There is exactly **one** effective audience used in the exported `ai_proxy_tools`:

1. `keelbase.tools.audience` if set;
2. otherwise `keelbase.delegation.audience`;
3. otherwise export fails with a clear 500 (and `/keelbase/status` reports the warning).

## Minimal production-ish example

```yaml
keelbase:
  delegation:
    secret: ${KEELBASE_DELEGATION_SECRET}
    audience: legacy-crm
    issuer: keelbase
    paths:
      - /api/compensation
  tools:
    base-url: ${SELF_BASE_URL:http://localhost:8081}
    export-enabled: true      # disable after the initial registration
    status-enabled: true      # diagnostics; disable in locked-down environments
  compensation:
    ledger-size: 4096
  client:
    base-url: ${KEELBASE_URL:http://localhost:3000}
    # audience: legacy-crm            # optional; falls back to delegation.audience
  audit:
    # base-url: ${GOVERNANCE_URL:}    # unset → local-only audit logging
    # api-key: ${GOVERNANCE_API_KEY:}
```

See [troubleshooting](troubleshooting.md) for the failure modes behind each required property.
