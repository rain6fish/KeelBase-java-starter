# 快速开始 — 10 分钟把 Java/Spring 系统接入 KeelBase

本指南带你让一个存量 Spring Boot 系统向 [KeelBase](https://github.com/rain6fish/KeelBase) 暴露受治理的 AI 工具。完成后你将获得：一个**读**工具（R1 自动执行）、一个**写**工具（R3 需人工确认）和一个**撤销补偿**端点（撤销 AI 副作用）——全部跑在你的真实数据上，全程过 KeelBase 治理层（委托身份 / 审计哈希链 / 撤销）。

完整参考：[配置参考](configuration.zh-CN.md) · [委托身份与授权](delegated-identity.zh-CN.md) · [工具声明](tool-declaration.zh-CN.md) · [补偿与撤销](compensation.zh-CN.md) · [排障](troubleshooting.zh-CN.md)。

---

## 0. 前置

- JDK 17+（Spring Boot 3.x）。
- 一个已启动的 [KeelBase](https://github.com/rain6fish/KeelBase)（示例默认 `localhost:3000`）。
- 两端共享同一个 `DELEGATION_SECRET`（≥ 32 字节）——见[委托身份与授权](delegated-identity.zh-CN.md)。

## 1. 引入依赖

> starter 尚未发布到 Maven Central（待 OSSRH 账号）。先在本地构建安装一次，再按常规消费：

```bash
# 构建并安装全部模块到本地仓库
git clone https://github.com/rain6fish/KeelBase-java-starter.git
cd KeelBase-java-starter
mvn install
```

**Maven** — 加入 `pom.xml`：

```xml
<dependency>
  <groupId>cn.com.keelbase</groupId>
  <artifactId>keelbase-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**Gradle** — 加入 `build.gradle`：

```groovy
implementation 'cn.com.keelbase:keelbase-spring-boot-starter:0.1.0-SNAPSHOT'
```

这一个依赖会带全：注解、委托验签过滤器、工具导出、补偿脚手架。classpath 含 Spring MVC 时全部自动装配。

## 2. 配置 `application.yml`

```yaml
keelbase:
  delegation:
    secret: ${KEELBASE_DELEGATION_SECRET}   # 与 KeelBase DELEGATION_SECRET 一致（≥ 32 字节）
    audience: legacy-crm                     # 目标系统标识，须等于 ai_proxy_tools 顶层 audience
    # 无 Authorization 头即拒绝的路径（fail-closed）：
    paths:
      - /api/compensation
  tools:
    base-url: http://localhost:8081          # 服务器根；工具 path 为完整路径（baseUrl + path）
    # audience: legacy-crm                   # 可选——缺省回退 delegation.audience
```

`secret` 与 `audience` 均为必填——缺任一项应用启动即失败并给出明确提示（见[排障](troubleshooting.zh-CN.md)）。

## 3. 注解三个方法

```java
@RestController
@RequestMapping("/api")
public class FollowupController extends KeelBaseCompensationSupport<Map<String, Object>> {

    private final Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public FollowupController(RevocationLedgerStore ledger, CompensationAuditSink auditSink) {
        super(ledger, auditSink);
    }

    // 读工具 → R1 自动执行
    @GetMapping("/followups")
    @KeelbaseTool(name = "list_followups", description = "列出跟进任务（读）")
    public List<Map<String, Object>> list() { return new ArrayList<>(store.values()); }

    // 写工具 → R3 需人工确认；可撤销
    @PostMapping("/followups")
    @KeelbaseTool(name = "create_followup",
                  description = "创建跟进任务（写，可撤销）",
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

    // KeelBase 撤销 AI 副作用时调用的补偿端点
    @DeleteMapping("/compensation/followups/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
        return handleRevoke(request, id, store::get,
                (item, subject) -> item.put("cancelled", true),
                "compensation.followups.revoke");
    }
}
```

## 4. 启动应用

```bash
mvn spring-boot:run        # 或从 IDE 启动；默认端口 8081
```

用诊断端点确认接线正确：

```bash
curl http://localhost:8081/keelbase/status
```

应看到 `delegation.secretConfigured: true`、解析后的 `export.audience`、工具清单以及任何配置告警。

## 5. 在 KeelBase 注册工具

```bash
# 1. 导出 ai_proxy_tools 配置
curl http://localhost:8081/keelbase/proxy-tools/export
```

复制返回的 JSON，写入 KeelBase（需要 admin token），**value 为 JSON 字符串**：

```bash
curl -X PUT http://localhost:3000/api/v1/settings/ai_proxy_tools \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer <admin-token>" \
  -d "{\"value\": $(curl -s http://localhost:8081/keelbase/proxy-tools/export | python -c 'import sys,json;print(json.dumps(json.load(sys.stdin)))'), \"type\": \"string\"}"
```

或粘贴到管理台「设置 → ai_proxy_tools」。**重启 KeelBase** 使新工具注册生效。

## 6. 端到端验证

KeelBase 与示例都已启动时，仓库内置脚本走完整闭环——确认门控 → 流式批准 → 委托身份写回 → 审计 → 撤销 → 补偿：

```bash
cd scripts
node verify-java-starter-e2e.mjs --configure   # 导出 + 写入 ai_proxy_tools（然后重启 KeelBase）
node verify-java-starter-e2e.mjs --verify       # 完整闭环（确定性 demo provider）
node verify-java-starter-e2e.mjs --verify --llm # 完整闭环（真实 LLM，需 API Key）
```

现在你的 Java 系统已具备受治理的 AI 工具面。下一步：[委托身份与授权](delegated-identity.zh-CN.md) 理解「AI 以谁的身份干活」，[工具声明](tool-declaration.zh-CN.md) 调优名称、风险级与参数。
