# Production Checklist

Use this before taking a Java/Spring system that's wired to KeelBase into production. It assumes the [quickstart](quickstart.md) flow already works in dev.

## 1. Pre-flight (dev stage)

- [ ] `keelbase.delegation.secret` set (≥ 32 bytes) and identical to KeelBase's `DELEGATION_SECRET`.
- [ ] `keelbase.delegation.audience` set and equal to the `ai_proxy_tools` top-level `audience`.
- [ ] `GET /keelbase/status` shows `delegation.secretConfigured: true`, the expected `export.audience`, and **no warnings**.
- [ ] `keelbase.tools.base-url` is the **server root** (no path prefix) and reachable from the KeelBase host.
- [ ] Compensation / `revokePath` endpoints are listed in `keelbase.delegation.paths` (fail-closed, so no anonymous call can reach them).
- [ ] The full e2e loop passed once: `scripts/verify-java-starter-e2e.mjs --configure` + `--verify`.

## 2. Hardening before go-live

- [ ] **Dedicated `DELEGATION_SECRET`** — KeelBase falls back to `JWT_SECRET` if not set; set a separate one and match it on the Java side. Rotating it must not require rotating user tokens.
- [ ] **One `audience` per target system** — it is the trust boundary between systems; don't reuse a single value across unrelated systems.
- [ ] **Disable the export endpoint** after registration: `keelbase.tools.export-enabled: false`, or restrict `/keelbase/proxy-tools/*` at the gateway/firewall. It reveals your internal tool paths.
- [ ] **Decide on `keelbase.tools.status-enabled`** — keep `true` if ops benefit from the diagnostics (it never leaks the secret); set `false` in a locked-down environment.
- [ ] **Keep the delegation TTL short** (KeelBase default 300s). Revocation is handled by compensation endpoints, not by long-lived tokens.
- [ ] **Remember the starter is additive** — outside `keelbase.delegation.paths` it is fail-open, so your endpoints still rely on your normal authentication for non-KeelBase traffic. Protected-path fail-closed is opt-in per prefix.

## 3. Operations

- [ ] Watch logs for `delegation.invalid` / `delegation.expired` bursts — a spike after a secret rotation or config change is a sign of drift.
- [ ] **Secret rotation**: update KeelBase and the Java side together; verify with `GET /keelbase/status` (`secretConfigured` + a delegated call).
- [ ] **Audit**: spot-check AI write side effects and their revocations in the KeelBase console, and confirm your `CompensationAuditSink` entries match on your side.
- [ ] Re-run `GET /keelbase/status` after any config change as the single-source health check.

## 4. Rollback / triage

If a delegated call starts failing:

1. `GET /keelbase/status` — is `secretConfigured` true? Any `warnings`?
2. KeelBase side — does `GET /api/v1/ai/tools` still show the proxy tools (config read at startup)?
3. Is the failing call hitting a protected path without a token, or a token whose `audience`/`issuer` no longer matches?
4. See [troubleshooting](troubleshooting.md) for each error code's cause and fix.
