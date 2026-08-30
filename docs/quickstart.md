# Quickstart — Wire a Java/Spring system into KeelBase in 10 minutes

This guide walks you through making an existing Spring Boot system expose governed AI tools to [KeelBase](https://github.com/rain6fish/KeelBase). After this you will have a **read** tool (auto-executed), a **write** tool (requires human confirmation) and a **revoke** endpoint (undoes the AI's side effect) — all running against your real data under KeelBase governance (delegated identity, audit chain, revocation).

Full reference: [configuration](configuration.md) · [delegated identity](delegated-identity.md) · [tool declaration](tool-declaration.md) · [compensation](compensation.md) · [troubleshooting](troubleshooting.md).

---

## 0. Prerequisites

- JDK 17+ (Spring Boot 3.x).
- A running [KeelBase](https://github.com/rain6fish/KeelBase) instance (`localhost:3000` in these examples).
- A shared `DELEGATION_SECRET` (≥ 32 bytes) configured on **both** sides — see [delegated identity](delegated-identity.md).

## 1. Add the dependency

Published to Maven Central — add `cn.com.keelbase:keelbase-spring-boot-starter:0.1.1` (0.1.0 is also live):

**Maven** — `pom.xml`:

```xml
<dependency>
  <groupId>cn.com.keelbase</groupId>
  <artifactId>keelbase-spring-boot-starter</artifactId>
  <version>0.1.1</version>
</dependency>
```

**Gradle** — `build.gradle`:

```groovy
implementation 'cn.com.keelbase:keelbase-spring-boot-starter:0.1.1'
```

> For the development snapshot (`0.1.2-SNAPSHOT`), install it locally once first:

```bash
git clone https://github.com/rain6fish/KeelBase-java-starter.git
cd KeelBase-java-starter
mvn install
```

That single dependency pulls in the annotation, delegation filter, tools export, and compensation scaffold. Everything auto-configures when the classpath contains Spring MVC.

## 2. Configure `application.yml`

```yaml
keelbase:
  delegation:
    secret: ${KEELBASE_DELEGATION_SECRET}   # same value as KeelBase DELEGATION_SECRET (≥ 32 bytes)
    audience: legacy-crm                     # target-system id; must match the ai_proxy_tools top-level audience
    # paths that are rejected when no Authorization header is present (fail-closed):
    paths:
      - /api/compensation
  tools:
    base-url: http://localhost:8081          # server root; tool paths are full paths (baseUrl + path)
    # audience: legacy-crm                   # optional — falls back to delegation.audience
```

Both `secret` and `audience` are required — the app fails fast at startup with a clear message if either is missing (see [troubleshooting](troubleshooting.md)).

## 3. Annotate three methods

```java
@RestController
@RequestMapping("/api")
public class FollowupController extends KeelBaseCompensationSupport<Map<String, Object>> {

    private final Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public FollowupController(RevocationLedgerStore ledger, CompensationAuditSink auditSink) {
        super(ledger, auditSink);
    }

    // read tool → R1, auto-executed
    @GetMapping("/followups")
    @KeelbaseTool(name = "list_followups", description = "List follow-up tasks (read)")
    public List<Map<String, Object>> list() { return new ArrayList<>(store.values()); }

    // write tool → R3, requires human confirmation; revocable
    @PostMapping("/followups")
    @KeelbaseTool(name = "create_followup",
                  description = "Create a follow-up task (write, revocable)",
                  revokePath = "DELETE /api/compensation/followups/{id}")
    public Map<String, Object> create(@RequestBody FollowupRequest req,
                                      @DelegationUser DelegationPrincipal principal) {
        long id = idSeq.getAndIncrement();
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("content", req.content());
        item.put("createdBy", principal == null ? "anonymous" : principal.identity());
        store.put(id, item);
        return item;
    }

    // compensation endpoint called by KeelBase when the AI side effect is revoked
    @DeleteMapping("/compensation/followups/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
        return handleRevoke(request, id, store::get,
                (item, subject) -> item.put("cancelled", true),
                "compensation.followups.revoke");
    }
}
```

## 4. Start the app

```bash
mvn spring-boot:run        # or run from your IDE; default port 8081
```

Check the diagnostic endpoint:

```bash
curl http://localhost:8081/keelbase/status
```

You should see `delegation.secretConfigured: true`, the resolved `export.audience`, the tool list, and any configuration warnings.

## 5. Register the tools in KeelBase

```bash
# 1. export the ai_proxy_tools config
curl http://localhost:8081/keelbase/proxy-tools/export
```

Copy the JSON and write it into KeelBase (admin token required), **value as a JSON string**:

```bash
curl -X PUT http://localhost:3000/api/v1/settings/ai_proxy_tools \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer <admin-token>" \
  -d "{\"value\": $(curl -s http://localhost:8081/keelbase/proxy-tools/export | python -c 'import sys,json;print(json.dumps(json.load(sys.stdin)))'), \"type\": \"string\"}"
```

Or paste it into the admin console **Settings → ai_proxy_tools**. **Restart KeelBase** for the new tools to register.

## 6. Verify end-to-end

With KeelBase and the example both running, the repo ships a script that walks the full loop — confirmation gate → streaming approve → proxy write with delegated identity → audit → revoke → compensation:

```bash
cd scripts
node verify-java-starter-e2e.mjs --configure   # export + write ai_proxy_tools (then restart KeelBase)
node verify-java-starter-e2e.mjs --verify       # full loop (deterministic demo provider)
node verify-java-starter-e2e.mjs --verify --llm # full loop with a real LLM (needs an API key)
```

You now have a governed AI tool surface on your Java system. Next: [delegated identity](delegated-identity.md) to understand who the AI acts as, and [tool declaration](tool-declaration.md) to tune names, risk levels, and parameters.
