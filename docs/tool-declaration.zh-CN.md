# 工具声明与导出

`@KeelbaseTool` 把一个 Spring MVC handler 方法变成受治理的 AI 工具。注解扫描器随后导出 `ai_proxy_tools` 配置，KeelBase 据此注册工具并决定治理策略（风险级 / 确认 / 审计 / 撤销）。

## 1. 在哪注解

`@RestController` 中任何已带 MVC 映射（`@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping`、`@RequestMapping`…）的方法上：

```java
@GetMapping("/followups")
@KeelbaseTool(name = "list_followups", description = "列出跟进任务（读）")
public List<Map<String, Object>> list() { ... }
```

扫描器通过 `RequestMappingHandlerMapping` 取方法，类级 `@RequestMapping("/api")` 前缀自动生效。

**类级标注**——把 `@KeelbaseTool` 标在 `@RestController` 类上，整个 controller 的**所有映射方法一键工具化**（工具名 = 方法名 `camelCase → snake_case`）；个别方法用 `@KeelbaseTool(enabled = false)` 排除：

```java
@RestController
@RequestMapping("/api/insights")
@KeelbaseTool(description = "CRM 分析工具（类级标注：所有映射方法工具化）")
public class CrmInsightsController {
    @GetMapping("/summary")
    public Map<String, Object> getCrmSummary() { ... }   // → 工具 get_crm_summary（R1）

    @GetMapping("/internal/health")                      // 不导出
    @KeelbaseTool(enabled = false)
    public Map<String, String> internalHealth() { ... }
}
```

方法级属性（`name`/`description`/`riskLevel`/`revokePath`）覆盖类级；类级标注下的方法 `enabled=false` 即排除。可运行示例见 `keelbase-java-crm-example`。

## 2. 注解属性

| 属性 | 缺省 | 说明 |
|---|---|---|
| `name` | 方法名 `camelCase → snake_case` | 工具名，须匹配 `^[a-z][a-z0-9_]{0,39}$`。重名自动改名为 `name_2`（会打 warn）。 |
| `description` | `METHOD /path`，或 `@Operation` summary/description | 给 LLM 看的描述。**务必填写**——模型靠它选工具。classpath 有 springdoc 且未显式填写时，自动从 `@Operation(summary/description)` 提取；显式 `@KeelbaseTool` 值始终优先。 |
| `riskLevel` | `AUTO` | `AUTO` 推断 GET→R1（读自动）/ POST·PUT·PATCH·DELETE→R3（写确认）。可显式设 R0–R5。 |
| `revokePath` | 无 | 相对 baseUrl 的补偿端点，如 `"DELETE /api/compensation/followups/{id}"`。KeelBase 撤销 AI 副作用时调用。 |
| `audience` | 全局 | 每工具 audience 覆盖。**仅当**部署真的支持 per-tool audience 才用；必须等于顶层 audience，否则验签失败。 |

风险语义（对齐 KeelBase 治理）：R0–R2 自动、R3 人工确认、R4 双人审批、R5 阻断。删除/审批/资金类工具建议 R4，破坏性操作 R5。

## 3. 参数提取

扫描器从方法签名推导工具参数：

| 来源 | 行为 |
|---|---|
| `@PathVariable Long id` | 必填路径参数（`integer`）。 |
| `@RequestParam` | 查询参数；有缺省值才非必填（导出 `默认: x`）；枚举类型导出可选值（`可选: A/B/C`）。**写方法**上还会进工具的 `queryParams`（KeelBase 以查询串发送而非 body）。 |
| `@RequestBody Dto dto` | 每个 DTO 字段成为一个参数——**对齐 Jackson**：含继承字段、跳过 `@JsonIgnore`/static/transient 字段、尊重 `@JsonProperty`（名与 required）、支持 record。 |
| `@DelegationUser` / `HttpServletRequest` / `Model` / servlet 类型 | 跳过（不是工具参数）。 |

类型映射（对齐 KeelBase 生成器）：

| Java | 工具类型 |
|---|---|
| `int/Integer/long/Long/short/Short/BigInteger` | `integer` |
| `float/double/BigDecimal` | `number` |
| `boolean/Boolean` | `boolean` |
| `String/char/Character/UUID/Enum/Date/LocalDate/LocalDateTime/OffsetDateTime/Instant` | `string` |
| `List/Map/嵌套对象` | `string`（Agent 传 JSON 文本） |

枚举字段会把选项列表作为参数描述导出（`可选: LOW/MEDIUM/HIGH`）。

## 4. baseUrl 约定

`keelbase.tools.base-url` 是**服务器根**，工具 `path` 是**完整路径**：

```yaml
keelbase:
  tools:
    base-url: http://legacy-crm:8081   # 根——不是 http://legacy-crm:8081/api
```

KeelBase 的 ProxyTool 用 `baseUrl + path` 拼接，所以上例中声明在 `@RequestMapping("/api/followups")` 的工具会被调用到 `http://legacy-crm:8081/api/followups`。`base-url` 尾斜杠会自动去掉。

## 5. 端点

| 端点 | 用途 |
|---|---|
| `GET /keelbase/proxy-tools/export` | `ai_proxy_tools` JSON（写入 KeelBase Settings → 重启生效）。`base-url`/`audience` 缺失时返回 500 并给出明确原因。可用 `keelbase.tools.export-enabled: false` 关闭。 |
| `GET /keelbase/status` | 诊断：委托配置、解析后的导出 audience、工具计数/名称、告警（audience 不一致、缺 base-url、无工具）。绝不泄露密钥。可用 `keelbase.tools.status-enabled: false` 关闭。 |

## 6. 问题可见性

扫描器对问题打 warn 而不是失败：非法工具名、无法解析的映射、名称冲突都会打印 `keelbase 工具跳过/冲突` 告警并带 handler 方法——工具从导出里消失时先查日志。

## 已知限制

- 参数名会清洗到 `^[a-z][a-zA-Z0-9_]{0,29}$` 以满足 KeelBase 契约；含非常规字符（如连字符）的 `@JsonProperty` 会被规范化为下划线。
- 复杂的嵌套 `@RequestBody` 字段映射为 `string`——Agent 传 JSON 文本，你的 Jackson 绑定负责解析。
