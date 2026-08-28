# KeelBase Java Starter

> **让存量 Java/Spring 系统获得受治理的 AI 能力**——基于 [KeelBase](https://github.com/rain6fish/KeelBase) 的 Spring Boot Starter：委托身份验签、代理工具导出、撤销补偿端点。Apache-2.0。

> KeelBase 是开源「企业 AI 信任运行时」。用本 Starter，你的存量 Java/Spring REST 端点即可成为 AI 工具——AI 在你的真实数据上干活，全程过 KeelBase 治理层（行级权限 / 写操作人工确认 / 防篡改审计哈希链 / 副作用可撤销）。

## 它能做什么

1. **委托身份**（`DelegationAuthFilter`）：校验 KeelBase 转发请求携带的委托 JWT（HS256 + audience + issuer + 过期），映射到本地用户，注入 `@DelegationUser DelegationPrincipal`。无 Spring Security 也能用；classpath 含 Security 时自动写入 SecurityContext。
2. **工具声明 + 导出**（`@KeelbaseTool`）：给 `@RestController` 方法加注解，`GET /keelbase/proxy-tools/export` 导出 `ai_proxy_tools` 配置——写入 KeelBase Settings 即注册为 AI 工具。类型/风险级/参数口径与 KeelBase 生成器对齐。
3. **补偿脚手架**（`KeelBaseCompensationSupport`）：AI 写副作用的撤销补偿端点——委托身份 + 幂等 + 审计，开箱即用。

## 快速开始（示例）

```bash
# 1. 启动示例（默认 8081）
cd keelbase-java-example
mvn spring-boot:run

# 2. 导出 ai_proxy_tools 配置
curl http://localhost:8081/keelbase/proxy-tools/export

# 3. 写入 KeelBase（PUT /settings/ai_proxy_tools，value 为导出 JSON 的字符串），重启 KeelBase
```

## 配置（application.yml）

```yaml
keelbase:
  delegation:
    secret: ${KEELBASE_DELEGATION_SECRET:}          # 必填，与 KeelBase DELEGATION_SECRET 一致（≥32 字节）
    audience: legacy-crm                             # 必填，须等于 ai_proxy_tools 顶层 audience
    issuer: keelbase                                 # 可选
    paths:                                           # 受保护路径（无 Authorization 头即拒绝）
      - /api/compensation
  tools:
    base-url: http://localhost:8081                  # 服务器根（导出；baseUrl + 完整 path 约定）
  compensation:
    ledger-size: 1024                                # 幂等账本 LRU 上限
```

## 注解示例

```java
@RestController
@RequestMapping("/api")
public class FollowupController extends KeelBaseCompensationSupport<Map<String, Object>> {

    @GetMapping("/followups")
    @KeelbaseTool(name = "list_followups", description = "列出跟进任务（读，R1 自动）")
    public List<Map<String, Object>> list() { ... }

    @PostMapping("/followups")
    @KeelbaseTool(name = "create_followup", description = "创建跟进任务（写，R3 需确认）",
                  revokePath = "DELETE /api/compensation/followups/{id}")
    public Map<String, Object> create(@RequestBody FollowupRequest req,
                                      @DelegationUser DelegationPrincipal principal) { ... }

    @DeleteMapping("/compensation/followups/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
        return handleRevoke(request, id, store::get,
                (item, subject) -> item.put("cancelled", true), "compensation.followups.revoke");
    }
}
```

## 模块

| 模块 | 说明 |
|---|---|
| `keelbase-tools-annotation` | `@KeelbaseTool` / `KeelbaseRiskLevel` / `@EnableKeelbaseTools` |
| `keelbase-delegation-filter` | 委托验签过滤器 + 用户映射 SPI + `@DelegationUser` |
| `keelbase-tools-export` | 注解扫描器 + 类型映射 + `/keelbase/proxy-tools/export` |
| `keelbase-compensation` | 补偿脚手架 + 幂等账本 + 审计钩子 |
| `keelbase-spring-boot-autoconfigure` | 自动装配（三件套 + 可选 Security 适配） |
| `keelbase-spring-boot-starter` | 聚合器（用户只引这个） |
| `keelbase-java-example` | 示例应用（不发布） |

## 契约（与 KeelBase 对齐）

- 委托 JWT：`{sub, oidcSub?, aud, iss:'keelbase', iat, exp}`，HS256，`DELEGATION_SECRET`。
- `ai_proxy_tools`：`{baseUrl, audience, tools:[{name, description, method, path, parameters, queryParams, riskLevel, revokePath}]}`；读 GET=R1 / 写 POST·PUT·PATCH·DELETE=R3；类型 integer/number/boolean/string（复杂结构→string）。
- `baseUrl` 为**服务器根**，工具 `path` 为**完整路径**（如 `/api/followups`）——KeelBase ProxyTool 用 `baseUrl + path` 拼接。
- 撤销：`proxy-revoker` 调补偿端点（带委托身份），须返回 2xx 且幂等。

## 明确不做（核心版）

Maven/Gradle plugin、Spring Boot 2 / Java 8 / WebFlux、Java 侧 Agent 编排、多租户、R4 双人审批 Java 特化。KeelBase 工具热更新需重启（配置写入 Settings 后重启生效）。

## 构建与测试

```bash
mvn install   # 编译全部模块 + 跑 JUnit（filter 6 场景 / 类型映射 / 补偿幂等 / 示例导出与补偿 e2e）
```

## 端到端验证

```bash
# 前置：KeelBase（localhost:3000）与示例（localhost:8081）已启动
node scripts/verify-java-starter-e2e.mjs --configure   # 导出 + 写入 ai_proxy_tools（然后重启 KeelBase）
node scripts/verify-java-starter-e2e.mjs --verify --llm # 完整闭环：确认门控 → 流式批准 → proxy 写回 → 审计 → 撤销 → 补偿
```

## License

Apache-2.0
