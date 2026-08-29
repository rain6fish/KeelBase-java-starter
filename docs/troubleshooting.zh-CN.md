# 排障与 FAQ

## 1. 错误码

委托调用失败返回 JSON `{"code": "<code>", "message": "..."}`：

| 错误码 | 状态 | 触发场景 |
|---|---|---|
| `delegation.missing` | 401 | 受保护路径上无 `Authorization` 头。 |
| `delegation.invalid` | 401 | 头不是 `Bearer <token>`，或验签失败（被篡改 / 密钥不一致）。 |
| `delegation.expired` | 401 | token 已过 `exp`。KeelBase token 缺省 300s。 |
| `delegation.audience_mismatch` | 403 | token `aud` ≠ `keelbase.delegation.audience`。 |
| `delegation.issuer_mismatch` | 403 | token `iss` ≠ `keelbase.delegation.issuer`。 |
| `compensation.invalid_result_id` | 400 | 补偿调用缺 `resultId`。 |

## 2. 启动失败

应用**启动即失败**并给出明确信息——这些是配置错误，不是偶发故障：

| 消息（节选） | 修复 |
|---|---|
| `keelbase.delegation.secret 未配置` | 配置 `keelbase.delegation.secret`（环境变量 `KEELBASE_DELEGATION_SECRET`），与 KeelBase `DELEGATION_SECRET` 一致。 |
| `keelbase.delegation.secret 无效（HS256 需 ≥32 字节）` | 密钥太短——HS256 要求 ≥ 32 字节。 |
| `keelbase.delegation.audience 未配置` | 配置 `keelbase.delegation.audience` 为目标系统标识（须等于 `ai_proxy_tools` 顶层 `audience`）。 |

## 3. 常见坑

- **两处 audience 不一致** —— `keelbase.tools.audience` 与 `keelbase.delegation.audience` 是独立属性。都配且不同时，导出工具带的 audience 会被过滤器拒绝（403）。通常**只配** `delegation.audience`；`tools.audience` 会回退到它。`/keelbase/status` 对不一致告警。
- **`base-url` 带 path 前缀或尾斜杠** —— 约定是服务器**根**（`http://host:8081`）+ **完整**工具 path。尾斜杠自动去除；带 `/api` 之类前缀会拼成双前缀。
- **导出里缺工具** —— 看应用日志的 `keelbase 工具跳过` 告警（非法名 / 无法解析映射）。`@KeelbaseTool` 方法没有 MVC 映射，或名字违反 `^[a-z][a-z0-9_]{0,39}$`，会被跳过并告警。
- **KeelBase 看不到新工具** —— `ai_proxy_tools` 配置在启动时读取：写完**重启 KeelBase**。用 `GET /api/v1/ai/tools` 验证。
- **所有委托调用都 403** —— 几乎必然是 audience 不一致（见上）或两端用了不同密钥。
- **`/keelbase/proxy-tools/export` 返回 404** —— `keelbase.tools.export-enabled` 为 `false`。
- **导出返回 500「缺少必填配置」** —— 配置 `keelbase.tools.base-url` 与 audience（显式或经 `delegation.audience`）。

## 4. 核对清单

接线完成后依次执行：

```bash
# 1. 诊断端点——一处看全
curl http://localhost:8081/keelbase/status
#    delegation.configured 与 secretConfigured：true
#    export.audience：期望值
#    warnings：应为空（或自解释）

# 2. 导出——合法 JSON、工具齐全、风险级符合预期
curl http://localhost:8081/keelbase/proxy-tools/export

# 3. 写入 KeelBase → 重启 → 确认注册
curl -H "Authorization: Bearer <admin-token>" http://localhost:3000/api/v1/ai/tools

# 4. 完整闭环（KeelBase + 示例都在运行）
node scripts/verify-java-starter-e2e.mjs --configure
node scripts/verify-java-starter-e2e.mjs --verify
```

## 5. 开启 debug 日志

扫描器的跳过/改名告警是 `WARN`（默认可见）。想看更多：

```yaml
logging:
  level:
    cn.com.keelbase: debug
```

## 6. FAQ

**必须用 Spring Security 吗？** 不用。委托过滤器与 `@DelegationUser` 不依赖 Security；只有 classpath 含 Security 时才消费它（写入上下文供 `@PreAuthorize`）。

**Spring Boot 2 / Java 8 呢？** starter 不支持（需 Boot 3.x / Java 17+）。走 API 代理路径（OpenAPI → `ai_proxy_tools`）或参考 KeelBase `docs/integrator-kit/java-compensation-example.md` 的独立补偿端点。

**能让 AI 完全用不了某个工具吗？** 给 `@KeelbaseTool` 设 `riskLevel = R5`——R5 在 KeelBase 治理层阻断执行。

**谁能撤销副作用？** KeelBase 管理台（admin），以及工作台执行轨迹的本人撤销。只有声明了 `revokePath` 的工具支持外部撤销。
