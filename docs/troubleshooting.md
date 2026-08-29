# Troubleshooting & FAQ

## 1. Error codes

Delegated calls return JSON `{"code": "<code>", "message": "..."}` with these codes:

| Code | Status | When |
|---|---|---|
| `delegation.missing` | 401 | Request on a protected path with no `Authorization` header. |
| `delegation.invalid` | 401 | Header not `Bearer <token>`, or signature verification failed (tampered / wrong secret). |
| `delegation.expired` | 401 | Token past `exp`. KeelBase tokens default to 300s. |
| `delegation.audience_mismatch` | 403 | Token `aud` ≠ `keelbase.delegation.audience`. |
| `delegation.issuer_mismatch` | 403 | Token `iss` ≠ `keelbase.delegation.issuer`. |
| `compensation.invalid_result_id` | 400 | Compensation called without a `resultId`. |

## 2. Startup failures

The app **fails fast** with a clear message — these are configuration bugs, not transient errors:

| Message (abridged) | Fix |
|---|---|
| `keelbase.delegation.secret 未配置` | Set `keelbase.delegation.secret` (env `KEELBASE_DELEGATION_SECRET`) to the same value as KeelBase's `DELEGATION_SECRET`. |
| `keelbase.delegation.secret 无效（HS256 需 ≥32 字节）` | The secret is too short — HS256 requires ≥ 32 bytes. |
| `keelbase.delegation.audience 未配置` | Set `keelbase.delegation.audience` to the target-system id (must equal the `ai_proxy_tools` top-level `audience`). |

## 3. Common mistakes

- **Two audiences disagree** — `keelbase.tools.audience` and `keelbase.delegation.audience` are independent. If both are set and differ, exported tools carry an audience the filter rejects (403). Normally configure **only** `delegation.audience`; `tools.audience` falls back to it. `/keelbase/status` warns on mismatch.
- **`base-url` with a path prefix or trailing slash** — the convention is server **root** (`http://host:8081`) + **full** tool path. A trailing slash is stripped; a path prefix like `/api` will double it. 
- **Tools missing from the export** — check the app log for `keelbase 工具跳过` warnings (invalid name, unresolvable mapping). A `@KeelbaseTool` method with no MVC mapping, or a name violating `^[a-z][a-z0-9_]{0,39}$`, is skipped with a warning.
- **KeelBase doesn't see the new tools** — the `ai_proxy_tools` setting is read at startup: **restart KeelBase** after writing the export. Verify with `GET /api/v1/ai/tools`.
- **All delegated calls 403** — almost always an audience mismatch (see above) or the two sides using different secrets.
- **`/keelbase/proxy-tools/export` returns 404** — `keelbase.tools.export-enabled` is `false`.
- **Export returns 500 "缺少必填配置"** — set `keelbase.tools.base-url` and an audience (explicit or via `delegation.audience`).

## 4. Verification checklist

Run these in order after wiring:

```bash
# 1. Diagnostics — everything you need in one place
curl http://localhost:8081/keelbase/status
#    delegation.configured & secretConfigured: true
#    export.audience: your expected audience
#    warnings: should be empty (or explain itself)

# 2. Export — valid JSON, tools present, risk levels as expected
curl http://localhost:8081/keelbase/proxy-tools/export

# 3. Write into KeelBase → RESTART KeelBase → confirm registration
curl -H "Authorization: Bearer <admin-token>" http://localhost:3000/api/v1/ai/tools

# 4. Full loop (KeelBase + example both running)
node scripts/verify-java-starter-e2e.mjs --configure
node scripts/verify-java-starter-e2e.mjs --verify
```

## 5. Enabling debug logging

The scanner's skip/rename warnings are logged at `WARN` (visible by default). To see more:

```yaml
logging:
  level:
    cn.com.keelbase: debug
```

## 6. FAQ

**Do I need Spring Security to use the starter?** No. The delegation filter and `@DelegationUser` work without it. Spring Security is only consumed when present (context write for `@PreAuthorize`).

**Spring Boot 2 / Java 8?** Not supported by the starter (Boot 3.x / Java 17+). Use the API-proxy path (OpenAPI → `ai_proxy_tools`) or the standalone compensation reference in KeelBase's `docs/integrator-kit/java-compensation-example.md`.

**Can the AI be blocked from a tool entirely?** Set `riskLevel = R5` on the `@KeelbaseTool` — R5 blocks execution in KeelBase governance.

**Who can revoke a side effect?** KeelBase admin console, and the acting user via the workbench trace. Only tools with a declared `revokePath` are externally revocable.
