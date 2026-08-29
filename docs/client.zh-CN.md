# KeelbaseClient — 委托 token 与审计上报

`keelbase-client` 模块让 Java/Spring 系统一等公民地使用 KeelBase 的两个能力：

1. **作为客户端使用委托身份**（`KeelbaseClient`）——用户会话持有 KeelBase JWT 时，获取限定 `audience` 的**短期委托 token**，缓存并按剩余有效期预刷新，用共享 `DELEGATION_SECRET` 本地验签。
2. **审计上报**（`KeelbaseAuditReporter`）——把 Java 侧业务动作上报到治理审计链（D2-3a `/external/audit`），让你的系统里发生了什么与 KeelBase 自身 AI 审计并列可见。

两者都由 starter 自动装配，你只需加配置 + 注入 bean。

---

## 1. KeelbaseClient — 委托 token 生命周期

### 场景

你的 Java 系统**作为客户端**接入 KeelBase：用户登录 KeelBase（或 SSO），你的系统持有他的 KeelBase access token，需要**代表他在系统间行动**。KeelBase 签发短期委托 JWT（`sub` = 用户 id、`aud` = 目标系统、默认 300s）——接收方如何验签见[委托身份](delegated-identity.zh-CN.md)。

### 配置（`keelbase.client.*`）

```yaml
keelbase:
  client:
    base-url: http://localhost:3000   # KeelBase 服务根（调 /api/v1/auth/delegation-token）
    audience: legacy-crm              # 可选；缺省回退 keelbase.delegation.audience
    # connect-timeout: 3s
    # read-timeout: 10s
```

### 用法

```java
@Autowired KeelbaseClient keelbaseClient;

// 1. 获取限定目标 audience 的委托 token（Bearer = 用户的 KeelBase JWT）
KeelbaseTokenIssue issue = keelbaseClient.obtain(userJwt, "legacy-crm", 300);
// issue.token() / subject()（oidcSub 或 local:<userId>）/ expiresIn() / userId() / audience()

// 2. 缓存 + 预刷新：同一 (jwt, audience) 复用缓存，剩余 ≤30s 时自动重取
String token = keelbaseClient.obtainAndCache(userJwt, "legacy-crm", 300);

// 3. 验签收到的委托 token（共享 DELEGATION_SECRET，HS256 + audience）
keelbaseClient.verify(token, "legacy-crm");   // 验签失败/aud 不符抛 KeelbaseClientException
```

契约（对齐 KeelBase `POST /api/v1/auth/delegation-token`）：

| 项 | 值 |
|---|---|
| 请求 | `{ audience: string, ttlSeconds?: 60-3600（默认 300）}`，`Authorization: Bearer <用户 JWT>` |
| 响应（解包 `data`） | `{ token, subject, expiresIn, userId, audience }` |
| 本地验签 | 用 `DELEGATION_SECRET` HS256 + `aud` + 过期 |

`keelbase.client.base-url` 未配置 → 仅 `verify` 可用；`obtain` 抛清晰配置错误。

---

## 2. KeelbaseAuditReporter — 上报 Java 侧动作

### 配置（`keelbase.audit.*`）

指向**治理台**（D2-3a）。`base-url` 为空 → reporter 禁用，仅本地日志（与 KeelBase 的 `GovernanceReporter` 同语义）。

```yaml
keelbase:
  audit:
    base-url: http://localhost:3001   # 治理台服务根
    api-key: ${GOVERNANCE_API_KEY:}   # 服务身份（x-api-key 头）
```

### 用法

```java
@Autowired KeelbaseAuditReporter audit;

audit.report(KeelbaseAuditEvent.builder()
        .userId("42")
        .username("alex")
        .action("compensation.followups.revoke")
        .detail("revoked followup id=7")
        .build());                    // source 缺省 "java"；异步、非阻塞、失败静默
```

事件以 `POST {base-url}/api/v1/external/audit` + `x-api-key` 上报，落治理库审计链，`source: "java"`。示例应用的 `FollowupController.revoke` 在真实补偿后上报演示。

---

## 相关

- [委托身份](delegated-identity.zh-CN.md) — 接收方如何验签委托 JWT（`DelegationAuthFilter`）
- [排障](troubleshooting.zh-CN.md) — `KeelbaseClientException` 常见原因
