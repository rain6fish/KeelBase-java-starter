# Delegated Identity & Authorization

When the AI (running in KeelBase) calls one of your Java endpoints, **whose identity does it carry?** The answer is KeelBase's delegated identity: a short-lived JWT that proves the call was initiated by KeelBase on behalf of a known user, scoped to your system (`audience`).

This page explains the token, how `DelegationAuthFilter` verifies it, how to map it to your local user, and how to enforce row-level ownership.

## 1. The delegation JWT

KeelBase issues it via `POST /api/v1/auth/delegation-token` (any authenticated KeelBase user). Payload:

```json
{
  "sub": "42",                 // KeelBase userId
  "oidcSub": "cn=wang.wu,...", // OIDC subject (unified identity source), optional
  "aud": "legacy-crm",         // target system — must match YOUR configured audience
  "iss": "keelbase",
  "iat": 1770000000,
  "exp": 1770000300            // default 300s TTL
}
```

- Signed **HS256** with the shared `DELEGATION_SECRET`.
- No OIDC? `sub` becomes `local:<userId>` and `identity()` returns the stripped number.
- Always scoped to one `audience` — a token for `legacy-erp` can never authenticate against `legacy-crm`.

## 2. What the filter verifies

`DelegationAuthFilter` runs for every request and enforces (all checked):

| Check | Failure |
|---|---|
| Signature valid (HS256, shared secret) | 401 `delegation.invalid` |
| Not expired | 401 `delegation.expired` |
| `aud` == `keelbase.delegation.audience` | 403 `delegation.audience_mismatch` |
| `iss` == `keelbase.delegation.issuer` (when set) | 403 `delegation.issuer_mismatch` |

**Fail-open vs fail-closed** — a request with **no** `Authorization` header:

- matches a prefix in `keelbase.delegation.paths` → **401** `delegation.missing` (fail-closed). Put your compensation/`revokePath` endpoints here.
- otherwise → **passes through** (fail-open), so non-KeelBase traffic is unaffected.

Requests with a present-but-invalid header are always rejected (401/403) regardless of path.

## 3. Getting the identity in your controller

```java
@PostMapping("/followups")
@KeelbaseTool(name = "create_followup", description = "...", revokePath = "DELETE /api/compensation/followups/{id}")
public Map<String, Object> create(@RequestBody FollowupRequest req,
                                  @DelegationUser DelegationPrincipal principal) {
    // principal.identity()  → "wang.wu" (oidcSub) or "42" (local:<userId> stripped)
    // principal.subject()   → "42" or "local:42"
    // principal.audience()  → "legacy-crm"
    return item;
}
```

`principal` is `null` when the request had no valid delegated identity (fail-open path).

## 4. Mapping to your local user (`KeelBaseUserMapper`)

The default mapper returns `principal.identity()` as a string. To resolve your `User` entity, implement the SPI and register it as a bean — it replaces the default:

```java
@Bean
KeelBaseUserMapper userMapper(UserRepository users) {
    return principal -> {
        String localId = principal.identity();
        return users.findByExternalId(localId); // Optional<User>
    };
}
```

The mapped object is stored in the request attribute `keelbase.delegation.user` (`DelegationAuthFilter.MAPPED_USER_ATTR`). `@DelegationUser` injects the raw `DelegationPrincipal`; read your mapped entity from the request attribute:

```java
@PostMapping("/followups")
public Map<String, Object> create(@RequestBody FollowupRequest req,
                                  @DelegationUser DelegationPrincipal principal,
                                  HttpServletRequest request) {
    MyLocalUser user = (MyLocalUser) request.getAttribute(DelegationAuthFilter.MAPPED_USER_ATTR);
    // user is your own entity resolved from the delegation identity (null when unmapped)
}
```

## 5. Spring Security integration

When `spring-security-core` is on the classpath, the starter registers `SecurityDelegationWriter` which copies the verified identity into `SecurityContextHolder` (a `UsernamePasswordAuthenticationToken` whose principal is the `DelegationPrincipal`), then clears it after the request. This makes `@PreAuthorize` and friends work for delegated calls:

```java
@PreAuthorize("isAuthenticated()")
@DeleteMapping("/api/compensation/followups/{id}")
public ResponseEntity<?> revoke(...) { ... }
```

The token carries no authorities — treat presence of the principal as "authenticated via KeelBase delegation" and do ownership checks yourself.

## 6. Row-level ownership (拒绝越权)

Delegation authenticates **who**, but the AI operates **your** data — you must enforce per-row ownership yourself. The delegated identity maps to the same local user, so your existing ownership rules apply unchanged:

```java
@PostMapping("/api/customers/{id}/followups")
@KeelbaseTool(name = "create_followup", description = "Create a follow-up (write)", revokePath = "DELETE /api/customers/{id}/followups/{fid}")
public Map<String, Object> create(@PathVariable Long id,
                                  @RequestBody FollowupRequest req,
                                  @DelegationUser DelegationPrincipal principal) {
    Customer c = customerRepo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    // 越权：客户不属于该用户 → 403，AI 会收到工具失败并回退
    if (!c.ownerId().equals(principal.identity())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your customer");
    }
    ...
}
```

KeelBase propagates your non-2xx as a tool failure the agent can react to — an over-privileged tool is also visible in the audit trail, so a 403 here is both a guard and a governance signal.

## 7. Operational hygiene

- **Use a dedicated `DELEGATION_SECRET`** separate from KeelBase's `JWT_SECRET` (the starter requires you to configure it explicitly; there is no insecure default).
- **One `audience` per target system** — it is the trust boundary between systems.
- **Keep the TTL short** (default 300s; KeelBase allows 60–3600). Revocation is a compensation-endpoint concern, not a token concern.
- **Protect compensation endpoints** by listing them in `keelbase.delegation.paths` so no anonymous call can reach them.
