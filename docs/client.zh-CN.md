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

// 3b. 验签并取解析后的身份（subject / oidcSub / audience / expiresAt）
Map<String, Object> identity = keelbaseClient.verifyAndGet(token, "legacy-crm");
// identity.get("oidcSub") -> 映射你的本地用户
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

## 3. KeelbasePolicyClient — 拉取治理策略

业务系统本地按治理台实时策略约束执行（工具开关 / 确认 / 角色白名单 / 审计粒度）。配置复用 `keelbase.audit.base-url` + `api-key`（治理台服务身份）：

```java
@Autowired KeelbasePolicyClient policy;

Optional<GovernancePolicy> opt = policy.fetch();
opt.ifPresent(p -> {
    // override（null = 不覆盖该维度，沿用本地默认）
    Boolean confirmation = p.tools().get("create_followup") == null
            ? null : p.tools().get("create_followup").requiresConfirmation();
    String granularity = p.auditGranularity();   // "all" | "write" | "off"
});
```

端点：治理台 `GET /api/v1/external/governance/policy` + `x-api-key`（与审计上报同服务身份）。`tools` 只含被覆盖字段（partial override），需与自身工具默认合并得「生效值」；未配置 `base-url/api-key` → `Optional.empty()`（本地全默认）；HTTP/解析失败抛 `KeelbaseClientException`。

---

## 4. 副作用状态反向查询

Java 侧反向对账：某个由 AI 发起的业务动作（如 `followup/7`）的副作用是否存在、是否已撤销。服务身份调主仓 `GET /api/v1/external/effects/:resultType/:resultId`：

```java
// 配置：keelbase.client.base-url（KeelBase 主应用）+ side-effect-api-key（= 主应用 GOVERNANCE_API_KEY）
@Autowired KeelbaseClient client;

SideEffectStatus s = client.querySideEffect("followup", 7);
if (!s.found()) {
    // 该业务动作无 AI 副作用记录（非 AI 创建 / 已不存在），按普通数据继续
} else if (s.revoked()) {
    // 已撤销（本地实体 = 目标软删）
} else if (s.revokeHint() != null) {
    // B 路径 proxy_call：撤销经 Java 补偿端点，撤销态需在 Java 侧确认（诚实边界）
}
```

配置（`keelbase.client.*`）：

```yaml
keelbase:
  client:
    base-url: http://localhost:3000          # KeelBase 主应用（调委托 token 同源）
    side-effect-api-key: ${GOVERNANCE_API_KEY}  # x-api-key 服务身份，需为主应用接受的 key
```

语义：**本地实体**（event/todo/crm_task 等）`revoked = targetSoftDeleted` 是撤销真值；**B 路径 `proxy_call`** 主库 effect 无撤销列、撤销走 Java 补偿端点 → `revokeHint` 明示「撤销态需在 Java 侧确认」，不夸大。HTTP 404 → `SideEffectStatus.notFound()`（`found=false`）；未配 `side-effect-api-key` / ≥300 → 抛 `KeelbaseClientException`。

---

## 相关

- [委托身份](delegated-identity.zh-CN.md) — 接收方如何验签委托 JWT（`DelegationAuthFilter`）
- [排障](troubleshooting.zh-CN.md) — `KeelbaseClientException` 常见原因
