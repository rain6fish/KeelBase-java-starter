# 委托身份与授权

当 KeelBase 里的 AI 调用你的 Java 端点时，**它以谁的身份干活？** 答案是 KeelBase 的委托身份：一个短期 JWT，证明该调用由 KeelBase 代表某个已知用户发起，并限定到你的系统（`audience`）。

本页说明 token 结构、`DelegationAuthFilter` 如何验签、如何映射到你的本地用户，以及如何做行级归属校验。

## 1. 委托 JWT

KeelBase 通过 `POST /api/v1/auth/delegation-token` 签发（任意已认证用户）。载荷：

```json
{
  "sub": "42",                 // KeelBase userId
  "oidcSub": "cn=wang.wu,...", // OIDC subject（统一身份源映射键），可选
  "aud": "legacy-crm",         // 目标系统——必须等于你配置的 audience
  "iss": "keelbase",
  "iat": 1770000000,
  "exp": 1770000300            // 缺省 300s
}
```

- **HS256** 签名，密钥为共享的 `DELEGATION_SECRET`。
- 无 OIDC 时 `sub` 为 `local:<userId>`，`identity()` 会剥掉前缀返回数字。
- 始终限定单个 `audience`——签给 `legacy-erp` 的 token 永远不能认证 `legacy-crm`。

## 2. 过滤器校验什么

`DelegationAuthFilter` 对每个请求执行以下全部校验：

| 校验 | 失败响应 |
|---|---|
| HS256 验签（共享密钥） | 401 `delegation.invalid` |
| 未过期 | 401 `delegation.expired` |
| `aud` == `keelbase.delegation.audience` | 403 `delegation.audience_mismatch` |
| `iss` == `keelbase.delegation.issuer`（配置时） | 403 `delegation.issuer_mismatch` |

**fail-open vs fail-closed** —— 请求**无** `Authorization` 头时：

- 命中 `keelbase.delegation.paths` 前缀 → **401** `delegation.missing`（fail-closed）。把补偿/`revokePath` 端点放在这里。
- 否则 → **放行**（fail-open），不影响非 KeelBase 流量。

带有但无效的 Authorization 头则无论路径一律拒绝（401/403）。

## 3. 在控制器里取身份

```java
@PostMapping("/followups")
@KeelbaseTool(name = "create_followup", description = "...", revokePath = "DELETE /api/compensation/followups/{id}")
public Map<String, Object> create(@RequestBody FollowupRequest req,
                                  @DelegationUser DelegationPrincipal principal) {
    // principal.identity()  → "wang.wu"（oidcSub）或 "42"（local:<userId> 去前缀）
    // principal.subject()   → "42" 或 "local:42"
    // principal.audience()  → "legacy-crm"
    return item;
}
```

无有效委托身份（fail-open 路径）时 `principal` 为 `null`。

## 4. 映射到本地用户（`KeelBaseUserMapper`）

默认映射返回 `principal.identity()` 字符串。要解析成你的 `User` 实体，实现 SPI 并注册为 bean 即可覆盖默认：

```java
@Bean
KeelBaseUserMapper userMapper(UserRepository users) {
    return principal -> users.findByExternalId(principal.identity()); // Optional<User>
}
```

映射结果写入 request attribute `keelbase.delegation.user`（`DelegationAuthFilter.MAPPED_USER_ATTR`）。`@DelegationUser` 注入的是原始 `DelegationPrincipal`；你的映射实体从 request attribute 读取：

```java
@PostMapping("/followups")
public Map<String, Object> create(@RequestBody FollowupRequest req,
                                  @DelegationUser DelegationPrincipal principal,
                                  HttpServletRequest request) {
    MyLocalUser user = (MyLocalUser) request.getAttribute(DelegationAuthFilter.MAPPED_USER_ATTR);
    // user 是从委托身份解析出的本地实体（未映射时为 null）
}
```

## 5. Spring Security 集成

classpath 含 `spring-security-core` 时，starter 注册 `SecurityDelegationWriter`，把验签通过的身份写入 `SecurityContextHolder`（一个 principal 为 `DelegationPrincipal` 的 `UsernamePasswordAuthenticationToken`），请求结束清理。这样 `@PreAuthorize` 等对委托调用生效：

```java
@PreAuthorize("isAuthenticated()")
@DeleteMapping("/api/compensation/followups/{id}")
public ResponseEntity<?> revoke(...) { ... }
```

token 不带任何权限——把「有 principal」理解为「已通过 KeelBase 委托认证」，归属校验由你自己做。

## 6. 行级归属（拒绝越权）

委托解决的是**身份**，而 AI 操作的是**你的数据**——行级归属必须由你执行。委托身份映射到同一个本地用户，你现有的归属规则原样可用：

```java
@PostMapping("/api/customers/{id}/followups")
@KeelbaseTool(name = "create_followup", description = "创建跟进（写）", revokePath = "DELETE /api/customers/{id}/followups/{fid}")
public Map<String, Object> create(@PathVariable Long id,
                                  @RequestBody FollowupRequest req,
                                  @DelegationUser DelegationPrincipal principal) {
    Customer c = customerRepo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    // 越权：客户不属于该用户 → 403，AI 收到工具失败并回退
    if (!c.ownerId().equals(principal.identity())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your customer");
    }
    ...
}
```

KeelBase 会把你的非 2xx 透传为工具失败供 Agent 回退——这里的 403 既是防线，也是治理信号（出现在审计轨迹里）。

## 7. 运维卫生

- **用独立的 `DELEGATION_SECRET`**，与 KeelBase 的 `JWT_SECRET` 分开（starter 要求显式配置，没有不安全的缺省值）。
- **每个目标系统一个 `audience`**——它是系统间信任边界。
- **保持 TTL 短**（缺省 300s；KeelBase 允许 60–3600）。撤销是补偿端点的事，不是 token 的事。
- **保护补偿端点**——把它们列入 `keelbase.delegation.paths`，杜绝匿名可达。
