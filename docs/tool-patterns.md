# Tool Patterns — turning real business endpoints into governed AI tools

Recipes for the common shapes a legacy Java system exposes, and how to declare them with the starter so the AI uses them correctly. Each pattern names the feature it leans on and points at a working example.

## 1. Paginated list

The most common read shape. Declare `page`/`limit` query params with descriptions + defaults, and return a stable envelope the model can page through.

```java
@GetMapping("/customers")
@KeelbaseTool(name = "list_customers", description = "Customer list (read), keyword filter + pagination")
public Map<String, Object> listCustomers(
        @RequestParam(required = false) @Parameter(description = "name/company keyword") String keyword,
        @RequestParam(required = false, defaultValue = "1") @Parameter(description = "page, 1-based") int page,
        @RequestParam(required = false, defaultValue = "20") @Parameter(description = "page size") int limit) {
    // ...
    return Map.of("items", pageItems, "total", total, "page", page, "limit", limit);
}
```

Exported params carry the descriptions **and** defaults (`页码（从 1 起）；默认: 1`), so the model pages without guessing. Example: `keelbase-java-crm-example` `CrmController.listCustomers`.

## 2. Keyword / filter params

Mark filter params with `@Parameter(description)` so the LLM knows what to pass. Any `@RequestParam` is exported; `required=false` or a `defaultValue` makes it optional.

## 3. Enum params

An enum `@RequestParam` exports its allowed values (`可选: LOW/MEDIUM/HIGH`), so the model picks valid options instead of inventing strings.

## 4. Descriptions from springdoc (optional)

When springdoc annotations are on the classpath, the starter picks up descriptions automatically — no need to repeat them in `@KeelbaseTool`:
- tool description ← `@Operation(summary/description)`
- path/query param description ← `@Parameter(description)`
- `@RequestBody` field description ← `@Schema(description)`

See [tool-declaration.md](tool-declaration.md) and the `CrmInsightsController` / `CrmController` examples.

## 5. Class-level annotation

Annotate the whole `@RestController` to tool up every mapped method in one go; exclude helpers with `@KeelbaseTool(enabled = false)`:

```java
@RestController
@RequestMapping("/api/insights")
@KeelbaseTool
public class CrmInsightsController {
    @GetMapping("/summary")
    @Operation(summary = "CRM summary: customers/orders/overdue")
    public Map<String, Object> getCrmSummary() { ... }   // tool get_crm_summary (R1)

    @GetMapping("/internal/health")
    @KeelbaseTool(enabled = false)
    public Map<String, String> internalHealth() { ... }  // not exported
}
```

## 6. Write tool + revocation

Writes are R3 (human confirmation) by default. Point `revokePath` at your compensation endpoint so KeelBase can undo the AI's side effect:

```java
@PostMapping("/customers/{id}/followups")
@KeelbaseTool(name = "create_followup_task", description = "Create follow-up (write, revocable)",
              revokePath = "DELETE /api/compensation/followups/{id}")
public FollowupTask create(@RequestBody CreateFollowupRequest req,
                           @DelegationUser DelegationPrincipal principal) { ... }

@DeleteMapping("/compensation/followups/{id}")
public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
    return handleRevoke(request, id, store::get, ...);   // idempotent + audit
}
```

Compensation endpoints are idempotent (KeelBase retries safely) and audit the revocation. See [compensation.md](compensation.md).

## 7. Complex JSON body

A nested/complex `@RequestBody` field maps to `string` — the model passes JSON text and your Jackson binding parses it. Flatten what the model should set explicitly (scalars, enums) into first-class params; leave the free-form part as a JSON string field.

## Reference examples

| Pattern | Example |
|---|---|
| pagination | `keelbase-java-crm-example` `list_customers` |
| enum params | `keelbase-java-example` `list_followups_by_customer` (priority) |
| defaults | `mark_followup_complete` (`done`, default true) |
| class-level + `@Operation` | `CrmInsightsController` |
| springdoc `@Parameter`/`@Schema` | `CrmController` |
| write + revocation | `create_followup_task` / `revoke` |
