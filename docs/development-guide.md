# Development Guide / 开发使用手册

> 从零把一个存量 Java / Spring 系统接入 KeelBase 的**完整开发路径**——依赖、配置、声明工具、委托身份、补偿撤销、自检调试、测试、发布。集成商按本手册一次接通。

## 0. 从脚手架开始（最快路径）

一条命令生成一个「已接治理的 AI 工具」Spring Boot 项目（读 R1 + 写 R3 + 补偿端点就绪）：

```bash
node scripts/new-keelbase-project.mjs --artifactId my-tools --audience legacy-app --dir ./my-tools
cd my-tools
export KEELBASE_DELEGATION_SECRET=<与 KeelBase 共享的密钥，≥32字节>
mvn spring-boot:run        # 启动
mvn keelbase:register      # 导出 + 写入 KeelBase（热更新生效，免重启）
```

参数：`--package`（默认 `cn.example`）/ `--port`（默认 8081）/ `--groupId`。骨架 = `keelbase-java-skeleton/`（两个 `@KeelbaseTool` + 补偿端点 + README），替换内存 Store 为你的 Service/DB 即可。

## 1. 依赖与配置

### 1.1 添加依赖（Maven / Gradle）

```xml
<dependency>
  <groupId>cn.com.keelbase</groupId>
  <artifactId>keelbase-spring-boot-starter</artifactId>
  <version>0.1.4</version>
</dependency>
```

```gradle
implementation 'cn.com.keelbase:keelbase-spring-boot-starter:0.1.4'
```

> 要求 **Spring Boot 3 + Java 17**。Boot 2 / Java 8 团队走 B 路径（零代码 API 代理，见 [boot2-java8-adapter](boot2-java8-adapter.md)）。

### 1.2 配置 `application.yml`

```yaml
keelbase:
  delegation:
    enabled: true
    secret: ${KEELBASE_DELEGATION_SECRET}   # 与 KeelBase DELEGATION_SECRET 一致（HS256，≥32 字节），生产必须 env 注入
    audience: legacy-crm                      # 必填；须等于 ai_proxy_tools 顶层 audience
    issuer: keelbase                          # 可选
    paths:                                    # 受保护路径（无 Authorization 头即拒绝）
      - /api/compensation
  tools:
    base-url: http://localhost:8081           # 服务器根（导出；baseUrl + 完整 path 约定）
    export-enabled: true                      # 注册完成后可关闭
    status-enabled: true                      # 生产可关诊断端点
  audit:
    base-url: ${KEELBASE_AUDIT_URL}           # 治理台服务根；空则不上报（本地日志回退）
    api-key: ${KEELBASE_AUDIT_KEY}            # 治理台服务身份（x-api-key 头）
```

**最小可用**：配 `delegation.secret` + `delegation.audience` + `tools.base-url` 即可导出工具。`audit` 可选（不配则业务动作不报治理台审计，仅本地日志）。

## 2. 声明 AI 工具（`@KeelbaseTool`）

### 2.1 方法级

```java
@RestController
@RequestMapping("/api")
public class FollowupController extends KeelBaseCompensationSupport<FollowupTask> {

    @GetMapping("/followups")
    @KeelbaseTool(name = "list_followups", description = "按客户筛选跟进任务（读，R1 自动）")
    public List<FollowupTask> list(@RequestParam Long customerId) { ... }

    @PostMapping("/followups")
    @KeelbaseTool(name = "create_followup", description = "创建跟进任务（写，R3 需确认）",
                  revokePath = "DELETE /api/compensation/followups/{id}")
    public FollowupTask create(@RequestBody FollowupRequest req,
                               @DelegationUser DelegationPrincipal principal) { ... }
}
```

### 2.2 类级（一个 Controller 一键工具化）

`@KeelbaseTool` 可标在 `@RestController` 类上——所有映射方法自动成为工具（名称 = 方法名 snake_case），`enabled=false` 排除辅助端点，方法级属性覆盖类级：

```java
@RestController
@RequestMapping("/api")
@KeelbaseTool
public class InsightsController {
    @GetMapping("/summary") public Map<String, Object> summary() { ... }   // → get_summary
    @GetMapping("/internal/health") @KeelbaseTool(enabled = false)
    public String health() { ... }                                        // → 排除
}
```

### 2.3 风险级（读自动 / 写确认）

- 读（GET）缺省 **R1 自动**；写（POST/PUT/PATCH/DELETE）缺省 **R3 需人工确认**
- `riskLevel` 可显式覆盖 R0–R5（R5 阻断 / R4 双人审批）

### 2.4 参数与描述（零样板）

- `@RequestParam` / `@PathVariable` / `@RequestBody` 自动提取参数（名称/类型/必填）
- 描述来源优先级：springdoc `@Parameter` / `@Schema` > 枚举可选值 + 默认值 > **自动 camelCase 分词**（`customerId` → `customer ID（integer）`）
- 有 springdoc 时无需重复写描述；无描述时零样板自动兜底

```java
@GetMapping("/orders")
@KeelbaseTool(description = "客户订单列表（读）")
public List<Order> orders(@RequestParam Long customerId,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int limit) { ... }
// 导出参数自动带描述：customerId（integer）、page（默认: 1）、limit（默认: 20）
```

## 3. 委托身份（`@DelegationUser`）

KeelBase 转发调用时携带委托 JWT，`DelegationAuthFilter` 验签（HS256 + aud/iss/exp），把委托主体注入方法参数：

```java
@GetMapping("/customers/{id}")
public Customer detail(@PathVariable Long id, @DelegationUser DelegationPrincipal principal) {
    // principal.subject() → KeelBase 映射的用户身份（oidcSub 或 local:<userId>）
    // 用它做行级归属校验
}
```

- 无 Spring Security 也能用；classpath 含 Security 时自动写 SecurityContext
- `secret` / `audience` 缺失 → 启动 fail-fast（不静默放行）
- 受保护路径（`delegation.paths`）无 Authorization 头 → 拒绝

## 4. 补偿与撤销

写工具（AI 副作用）应配 `revokePath` + 补偿端点（继承 `KeelBaseCompensationSupport` 得幂等 + 审计 + 委托身份）：

```java
@DeleteMapping("/compensation/followups/{id}")
public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
    return handleRevoke(request, id, store::get,
            (item, subject) -> item.put("cancelled", true), "compensation.followups.revoke");
}
```

KeelBase 撤销 AI 写副作用时调用该端点。`keelbase/status` 会报告 `revokeCovered`（可撤销写工具数），无 revokePath 的写工具会警告。

## 5. 接入自检与调试

### 5.1 接入健康度 `GET /keelbase/status`

```json
{
  "delegation": { "configured": true, "secretConfigured": true, "audience": "legacy-crm" },
  "tools": { "count": 5, "riskDistribution": { "R1": 3, "R3": 2 }, "revokeCovered": 2 },
  "audit": { "enabled": true, "configured": false },
  "health": { "status": "healthy", "summary": "接入配置完整，工具可正常导出" },
  "errors": [], "warnings": []
}
```

`health.status`：`healthy` / `degraded`（有提示）/ `error`（有阻断）。`errors` 是阻断问题（audience 不一致 / base-url 缺失 / 无工具），`warnings` 是提示（写无 revokePath / audit 未配置 / export 关闭）。

### 5.2 本地自检（不依赖 KeelBase）

```bash
node scripts/verify-java-local.mjs http://localhost:8081
# ① 接入健康度 ② 工具导出契约 ③ 委托验签门控（无 token 401 / 构造委托 token 幂等 200）
```

### 5.3 工具导出 `GET /keelbase/proxy-tools/export`

导出 `ai_proxy_tools` 配置（写工具含 `revokePath`），写入 KeelBase Settings 即注册为 AI 工具（重启 KeelBase 生效）。

## 6. 参考项目与配方

| 参考项目 | 域 | 演示 |
|---|---|---|
| [CRM](reference-project-crm.md) | 客户/订单/跟进 | 读 R1 + 写 R3 确认 + 撤销 |
| [PM](reference-project-pm.md) | 项目/任务 | 项目延期风险 + 写确认 + 撤销 |
| [Approval](reference-project-approval.md) | 审批流 | AI 预审（自动通过/转人工）+ 撤销恢复 |

常用配方见 [tool-patterns](tool-patterns.md)：分页、关键字筛选、枚举参数、springdoc 描述、类级工具、写 + 撤销、批量写。

## 7. 测试

每个参考项目带 JUnit 集成测试（MockMvc，无需真实 KeelBase）覆盖：工具导出契约（读 R1/写 R3 + revokePath）、确认门控、幂等撤销、委托身份、不泄密钥：

```bash
mvn -pl keelbase-java-example -am test -Dtest=ExportIntegrationTest
```

### 7.1 接入合规契约测试（`keelbase-test-support`）

把你自己的接入合规验证内建进 CI——加依赖 `cn.com.keelbase:keelbase-test-support`（test scope），写一个继承 `KeelbaseContractTest` 的 `@SpringBootTest` 类即可自动断言：

- 导出契约：工具非空、audience 一致、写工具必带 `revokePath`
- 受保护补偿路径：无 Authorization → 401；携带共享密钥签发的委托 JWT → 2xx 幂等

```java
@SpringBootTest
class MyContractComplianceTest extends KeelbaseContractTest {
}
```

## 8. 发布与升级

- **发布**：`mvn -Prelease deploy` 或打 `v*` tag 触发 GitHub Actions 自动发布 Central（见 [release-central](release-central.md)）
- **升级**：API 向后兼容（0.1.x 增量）；升级后跑 `verify-java-local.mjs` 确认接入健康
- **版本策略**：0.1.x 迭代 → 集成商试用反馈后定 1.0 API 冻结

## 双语文档速查

[Quick Start](quickstart.md) · [Configuration](configuration.md) · [Delegated Identity](delegated-identity.md) · [Tool Declaration](tool-declaration.md) · [Compensation](compensation.md) · [Client](client.md) · [Troubleshooting](troubleshooting.md) · [Production Checklist](production-checklist.md) · [Gradle](gradle-usage.md) · [Governance Visibility](governance-visibility.md) · [Boot 2 / Java 8](boot2-java8-adapter.md)
