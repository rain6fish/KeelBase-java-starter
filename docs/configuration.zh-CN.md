# 配置参考

所有配置项位于 `keelbase.*` 前缀下。支持 Spring Boot [relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html)——同一值可来自 `application.yml`、环境变量或 `--key=value` 启动参数。

| 配置项 | 类型 | 缺省 | 必填 | 说明 |
|---|---|---|---|---|
| `keelbase.delegation.enabled` | boolean | `true` | — | 委托验签总开关。`false` 时不注册 `DelegationAuthFilter`。 |
| `keelbase.delegation.secret` | string | — | **是** | 与 KeelBase `DELEGATION_SECRET` 共享的 HS256 密钥（≥ 32 字节）。缺失时**启动即失败**。环境变量：`KEELBASE_DELEGATION_SECRET`。 |
| `keelbase.delegation.audience` | string | — | **是** | 目标系统标识。必须等于 `ai_proxy_tools` 顶层 `audience`，否则 KeelBase 转发的请求验签失败。缺失时**启动即失败**。 |
| `keelbase.delegation.issuer` | string | `keelbase` | — | 期望的 `iss` 声明。置空可跳过 issuer 校验。 |
| `keelbase.delegation.paths` | list[string] | `[]` | — | 受保护路径前缀（如 `/api/compensation`）。命中这些路径且**无** `Authorization` 头的请求直接 401（fail-closed）；未列路径 fail-open。 |
| `keelbase.tools.enabled` | boolean | `true` | — | 工具导出 + 诊断端点总开关。 |
| `keelbase.tools.base-url` | string | — | *导出需要* | 目标系统服务器根（如 `http://legacy-crm:8081`）。导出时自动去尾部斜杠。缺失时导出返回 500 并给出明确原因。 |
| `keelbase.tools.audience` | string | — | — | 导出 audience 的可选覆盖。**缺省回退 `keelbase.delegation.audience`**——通常只需配置 `delegation.audience`。若两者都配，必须一致（不一致会被 `/keelbase/status` 告警并导致委托验签失败）。 |
| `keelbase.tools.export-enabled` | boolean | `true` | — | 控制 `GET /keelbase/proxy-tools/export`。注册完成后生产可关。 |
| `keelbase.tools.status-enabled` | boolean | `true` | — | 控制 `GET /keelbase/status`（诊断端点，绝不泄露密钥）。 |
| `keelbase.tools.strict` | boolean | `false` | — | 启动 fail-fast：为 `true` 时若扫描发现 `@KeelbaseTool` 声明非法（无法解析 method/path、工具名非法）被跳过，则**应用启动失败**并列出明细，替代默认只打 WARN 跳过导致的「导出缺工具」困惑。 |
| `keelbase.compensation.ledger-size` | integer | `1024` | — | 补偿端点的进程内幂等账本 LRU 上限。 |
| `keelbase.client.base-url` | string | — | — | KeelBase 服务根（如 `http://localhost:3000`），调 `POST /api/v1/auth/delegation-token`。未配置 → `KeelbaseClient.obtain` 不可用（仅本地 `verify`）。见[客户端与审计上报](client.zh-CN.md)。 |
| `keelbase.client.audience` | string | — | — | 委托 token 的目标 audience。**缺省回退 `keelbase.delegation.audience`。** |
| `keelbase.client.connect-timeout` / `read-timeout` | duration | `3s` / `10s` | — | 委托 token 调用的 HTTP 超时。 |
| `keelbase.audit.base-url` | string | — | — | 治理台服务根（如 `http://localhost:3001`），审计上报 D2-3a `/external/audit`。未配置 → 上报禁用（仅本地日志）。 |
| `keelbase.audit.api-key` | string | — | — | 治理台服务身份（`GOVERNANCE_API_KEY`），`x-api-key` 头。 |
| `keelbase.audit.enabled` | boolean | `true` | — | 审计上报总开关；`base-url` 未配置时仍整体禁用。 |

## 环境变量

Spring Boot relaxed binding 允许用 `KEELBASE_DELEGATION_SECRET` 注入 `keelbase.delegation.secret`。list 用下标形式（如 `KEELBASE_DELEGATION_PATHS_0=/api/compensation`）。生产建议把共享密钥放环境变量、不写入 `application.yml`：

```yaml
keelbase:
  delegation:
    secret: ${KEELBASE_DELEGATION_SECRET}
```

## audience 解析规则

导出的 `ai_proxy_tools` 只有一个生效的 audience：

1. 配置了 `keelbase.tools.audience` 则用它；
2. 否则回退 `keelbase.delegation.audience`；
3. 再否则导出返回明确 500（`/keelbase/status` 会告警）。

## 最小生产示例

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
    export-enabled: true      # 初次注册后可关
    status-enabled: true      # 诊断用；高安全环境可关
  compensation:
    ledger-size: 4096
  client:
    base-url: ${KEELBASE_URL:http://localhost:3000}
    # audience: legacy-crm            # 可选；缺省回退 delegation.audience
  audit:
    # base-url: ${GOVERNANCE_URL:}    # 未配置 → 仅本地审计日志
    # api-key: ${GOVERNANCE_API_KEY:}
```

各必填项对应的失败模式见[排障](troubleshooting.zh-CN.md)。
