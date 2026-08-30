# Tool Declaration & Export

`@KeelbaseTool` turns a Spring MVC handler method into a governed AI tool. The annotation scanner then exports an `ai_proxy_tools` config that KeelBase consumes to register the tool and decide its governance (risk level, confirmation, audit, revocation).

## 1. Where to annotate

On any method of a `@RestController` that already carries an MVC mapping (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@RequestMapping`, …):

```java
@GetMapping("/followups")
@KeelbaseTool(name = "list_followups", description = "List follow-up tasks (read)")
public List<Map<String, Object>> list() { ... }
```

The scanner picks methods up through `RequestMappingHandlerMapping`, so class-level `@RequestMapping("/api")` prefixes are honored automatically.

**Class-level annotation** — put `@KeelbaseTool` on the `@RestController` itself to tool up **every** mapped method in one go (tool name = method name `camelCase → snake_case`); exclude a specific method with `@KeelbaseTool(enabled = false)`:

```java
@RestController
@RequestMapping("/api/insights")
@KeelbaseTool(description = "CRM analysis tools (class-level: all mapped methods)")
public class CrmInsightsController {
    @GetMapping("/summary")
    public Map<String, Object> getCrmSummary() { ... }   // → tool get_crm_summary (R1)

    @GetMapping("/internal/health")                      // not exported
    @KeelbaseTool(enabled = false)
    public Map<String, String> internalHealth() { ... }
}
```

Method-level attributes (`name`/`description`/`riskLevel`/`revokePath`) override the class-level ones; `enabled=false` on a method under a class-level annotation excludes it. See `keelbase-java-crm-example` for a working class-level controller.

## 2. Annotation attributes

| Attribute | Default | Meaning |
|---|---|---|
| `name` | method name, `camelCase → snake_case` | Tool name. Must match `^[a-z][a-z0-9_]{0,39}$`. A duplicate name is auto-renamed to `name_2` (a warning is logged). |
| `description` | `METHOD /path` | LLM-facing description. **Set it** — it is the only thing the model has to choose the right tool. |
| `riskLevel` | `AUTO` | `AUTO` infers GET→R1 (read, auto) / POST·PUT·PATCH·DELETE→R3 (write, confirm). Set explicitly to any of R0–R5. |
| `revokePath` | none | Compensation endpoint relative to `baseUrl`, e.g. `"DELETE /api/compensation/followups/{id}"`. Used by KeelBase to undo the AI's side effect. |
| `audience` | global | Per-tool audience override. **Only** use if your deployment ever supports per-tool audiences; it must equal the top-level audience or verification fails. |

Risk semantics (aligned with KeelBase governance): R0–R2 auto, R3 human confirmation, R4 dual approval, R5 blocked. Deleting/approving/financial tools deserve R4; destructive ones R5.

## 3. Parameter extraction

The scanner derives tool parameters from the method signature:

| Source | Behavior |
|---|---|
| `@PathVariable Long id` | Required path parameter (`integer`). |
| `@RequestParam` | Query parameter; `required` unless it has a default value (exported as `默认: x`). Enum parameters export their allowed values (`可选: A/B/C`). On **write** methods it is also added to the tool's `queryParams` (KeelBase sends it as a query string instead of body). |
| `@RequestBody Dto dto` | Each DTO field becomes a parameter — **Jackson-aware**: inherited fields included, `@JsonIgnore`/static/transient fields skipped, `@JsonProperty` respected for name & `required`, records supported. |
| `@DelegationUser` / `HttpServletRequest` / `Model` / servlet types | Skipped (not tool parameters). |

Type mapping (aligned with KeelBase's generator):

| Java | Tool type |
|---|---|
| `int/Integer/long/Long/short/Short/BigInteger` | `integer` |
| `float/double/BigDecimal` | `number` |
| `boolean/Boolean` | `boolean` |
| `String/char/Character/UUID/Enum/Date/LocalDate/LocalDateTime/OffsetDateTime/Instant` | `string` |
| `List/Map/nested objects` | `string` (the agent sends a JSON text) |

Enum fields export the option list as the parameter description (`可选: LOW/MEDIUM/HIGH`).

## 4. baseUrl convention

`keelbase.tools.base-url` is the **server root** and tool `path` is the **full path**:

```yaml
keelbase:
  tools:
    base-url: http://legacy-crm:8081   # root — NOT http://legacy-crm:8081/api
```

KeelBase's ProxyTool concatenates `baseUrl + path`, so with the example above a tool declared at `@RequestMapping("/api/followups")` is called at `http://legacy-crm:8081/api/followups`. A trailing slash in `base-url` is stripped automatically.

## 5. Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /keelbase/proxy-tools/export` | The `ai_proxy_tools` JSON (write into KeelBase Settings → restart). Returns 500 with a clear message when `base-url`/`audience` are missing. Disable with `keelbase.tools.export-enabled: false`. |
| `GET /keelbase/status` | Diagnostics: delegation config, resolved export audience, tool count/names, warnings (audience mismatch, missing base-url, no tools). Never leaks the secret. Disable with `keelbase.tools.status-enabled: false`. |

## 6. Visibility of problems

The scanner logs warnings instead of failing: an invalid tool name, an unresolvable mapping, or a name conflict all print a `keelbase 工具跳过/冲突` warning with the handler method — check your logs if a tool is missing from the export.

## Known limitations

- Parameter names are sanitized to `^[a-z][a-zA-Z0-9_]{0,29}$` to satisfy KeelBase's contract; a `@JsonProperty` with unusual characters (e.g. hyphens) is normalized to underscores.
- A complex nested `@RequestBody` field maps to `string` — the agent passes JSON text and your Jackson binding parses it.
