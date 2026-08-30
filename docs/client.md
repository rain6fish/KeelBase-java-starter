# KeelbaseClient — Delegation tokens & audit reporting

The `keelbase-client` module gives Java/Spring systems first-class access to two KeelBase capabilities:

1. **Delegated identity as a client** (`KeelbaseClient`) — when a user session holds a KeelBase JWT, obtain a short-lived **delegation token** bound to a target `audience`, cache it with proactive refresh, and verify it locally with the shared `DELEGATION_SECRET`.
2. **Audit reporting** (`KeelbaseAuditReporter`) — report Java-side business actions to the governance audit chain (D2-3a `/external/audit`), so what happened in your system is visible alongside KeelBase's own AI audit.

Both are auto-configured by the starter; you only add properties and inject the beans.

---

## 1. KeelbaseClient — delegation-token lifecycle

### Scenario

Your Java system integrates with KeelBase **as a client**: a user signs into KeelBase (or SSO), your system holds their KeelBase access token, and you need to act **on their behalf** across system boundaries. KeelBase signs a short-lived delegation JWT (`sub` = their user id, `aud` = your target system, default 300s) — see [delegated identity](delegated-identity.md) for how receivers verify it.

### Configuration (`keelbase.client.*`)

```yaml
keelbase:
  client:
    base-url: http://localhost:3000   # KeelBase service root (for /api/v1/auth/delegation-token)
    audience: legacy-crm              # optional; falls back to keelbase.delegation.audience
    # connect-timeout: 3s
    # read-timeout: 10s
```

### Usage

```java
@Autowired KeelbaseClient keelbaseClient;

// 1. Obtain a delegation token bound to the target audience (Bearer = the user's KeelBase JWT)
KeelbaseTokenIssue issue = keelbaseClient.obtain(userJwt, "legacy-crm", 300);
// issue.token() / subject() (oidcSub or local:<userId>) / expiresIn() / userId() / audience()

// 2. Cache + proactive refresh: same (jwt, audience) reuses the cached token until ≤30s remain
String token = keelbaseClient.obtainAndCache(userJwt, "legacy-crm", 300);

// 3. Verify a delegation token you received (shared DELEGATION_SECRET, HS256 + audience)
keelbaseClient.verify(token, "legacy-crm");   // throws KeelbaseClientException on bad sig / wrong aud

// 3b. Verify and get the parsed identity (subject / oidcSub / audience / expiresAt)
Map<String, Object> identity = keelbaseClient.verifyAndGet(token, "legacy-crm");
// identity.get("oidcSub") -> map to your local user
```

Contract (matches KeelBase `POST /api/v1/auth/delegation-token`):

| Item | Value |
|---|---|
| Request | `{ audience: string, ttlSeconds?: 60-3600 (default 300) }`, `Authorization: Bearer <user JWT>` |
| Response (unwrapped `data`) | `{ token, subject, expiresIn, userId, audience }` |
| Verified locally | HS256 with `DELEGATION_SECRET` + `aud` + expiry |

`keelbase.client.base-url` unset → only `verify` is available; `obtain` throws a clear configuration error.

---

## 2. KeelbaseAuditReporter — report Java-side actions

### Configuration (`keelbase.audit.*`)

Points at the **governance control plane** (D2-3a). Unset `base-url` → reporter is disabled and only logs locally (same semantics as KeelBase's `GovernanceReporter`).

```yaml
keelbase:
  audit:
    base-url: http://localhost:3001   # governance control plane root
    api-key: ${GOVERNANCE_API_KEY:}   # service identity (x-api-key header)
```

### Usage

```java
@Autowired KeelbaseAuditReporter audit;

audit.report(KeelbaseAuditEvent.builder()
        .userId("42")
        .username("alex")
        .action("compensation.followups.revoke")
        .detail("revoked followup id=7")
        .build());                    // source defaults to "java"; async, non-blocking, silent on failure
```

The event is sent as `POST {base-url}/api/v1/external/audit` with `x-api-key`, landing in the governance audit chain with `source: "java"`. See the example app's `FollowupController.revoke`, which reports after a real compensation.

---

## Related

- [delegated identity](delegated-identity.md) — how receivers verify delegation JWTs (`DelegationAuthFilter`)
- [troubleshooting](troubleshooting.md) — `KeelbaseClientException` causes
