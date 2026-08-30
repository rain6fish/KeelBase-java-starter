# 工具模式——把真实业务端点变成受治理的 AI 工具

存量 Java 系统常见端点形状的接入配方：如何用 starter 声明它们，让 AI 正确使用。每个模式注明依赖的特性 + 指向可运行示例。

## 1. 分页列表

最常见的读形状。声明 `page`/`limit` 查询参数（带描述 + 默认值），返回稳定的分页结构供模型翻页。

```java
@GetMapping("/customers")
@KeelbaseTool(name = "list_customers", description = "客户列表（读），关键字筛选 + 分页")
public Map<String, Object> listCustomers(
        @RequestParam(required = false) @Parameter(description = "名称/公司关键字") String keyword,
        @RequestParam(required = false, defaultValue = "1") @Parameter(description = "页码（从 1 起）") int page,
        @RequestParam(required = false, defaultValue = "20") @Parameter(description = "每页条数") int limit) {
    // ...
    return Map.of("items", pageItems, "total", total, "page", page, "limit", limit);
}
```

导出的参数带描述**和**默认值（如 `页码（从 1 起）；默认: 1`），模型无需猜测即可翻页。示例：`keelbase-java-crm-example` 的 `CrmController.listCustomers`。

## 2. 关键字/筛选参数

筛选参数用 `@Parameter(description)` 标注，LLM 知道传什么。任何 `@RequestParam` 都会导出；`required=false` 或 `defaultValue` 使其可选。

## 3. 枚举参数

枚举类型的 `@RequestParam` 导出可选值（`可选: LOW/MEDIUM/HIGH`），模型选择合法选项而非杜撰字符串。

## 4. springdoc 描述自动提取（可选）

classpath 有 springdoc 注解时，描述自动提取，不必在 `@KeelbaseTool` 重复写：
- 工具描述 ← `@Operation(summary/description)`
- path/query 参数描述 ← `@Parameter(description)`
- `@RequestBody` 字段描述 ← `@Schema(description)`

见 [工具声明](tool-declaration.zh-CN.md) 与 `CrmInsightsController` / `CrmController` 示例。

## 5. 类级标注

在 `@RestController` 类上标一个注解，所有映射方法一键工具化；用 `@KeelbaseTool(enabled = false)` 排除辅助端点：

```java
@RestController
@RequestMapping("/api/insights")
@KeelbaseTool
public class CrmInsightsController {
    @GetMapping("/summary")
    @Operation(summary = "CRM 汇总：客户数/订单数/逾期订单数")
    public Map<String, Object> getCrmSummary() { ... }   // 工具 get_crm_summary（R1）

    @GetMapping("/internal/health")
    @KeelbaseTool(enabled = false)
    public Map<String, String> internalHealth() { ... }  // 不导出
}
```

## 6. 写工具 + 撤销

写操作默认 R3（需人工确认）。`revokePath` 指向你的补偿端点，KeelBase 可撤销 AI 副作用：

```java
@PostMapping("/customers/{id}/followups")
@KeelbaseTool(name = "create_followup_task", description = "创建跟进（写，可撤销）",
              revokePath = "DELETE /api/compensation/followups/{id}")
public FollowupTask create(@RequestBody CreateFollowupRequest req,
                           @DelegationUser DelegationPrincipal principal) { ... }

@DeleteMapping("/compensation/followups/{id}")
public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
    return handleRevoke(request, id, store::get, ...);   // 幂等 + 审计
}
```

补偿端点是幂等的（KeelBase 安全重试）并审计撤销。见[补偿与撤销](compensation.zh-CN.md)。

## 7. 复杂 JSON body

嵌套/复杂 `@RequestBody` 字段映射为 `string`——模型传 JSON 文本，你的 Jackson 绑定负责解析。把模型应显式设置的（标量/枚举）扁平化为一级参数；自由格式部分留作 JSON 字符串字段。

## 参考示例

| 模式 | 示例 |
|---|---|
| 分页 | `keelbase-java-crm-example` 的 `list_customers` |
| 枚举参数 | `keelbase-java-example` 的 `list_followups_by_customer`（priority） |
| 默认值 | `mark_followup_complete`（`done`，默认 true） |
| 类级 + `@Operation` | `CrmInsightsController` |
| springdoc `@Parameter`/`@Schema` | `CrmController` |
| 写 + 撤销 | `create_followup_task` / `revoke` |
