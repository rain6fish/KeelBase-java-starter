# 补偿与撤销

当 KeelBase 里的 AI 写入了你的 Java 系统、事后需要撤销这个副作用时，KeelBase 会**回调你的补偿端点**。starter 的 `KeelBaseCompensationSupport` 脚手架消除了样板：委托身份校验、幂等、审计都由基类处理——你只提供「怎么取消」的业务逻辑。

## 1. 调用契约

KeelBase 的 `proxy-revoker` 带委托身份调用你的补偿端点：

```
DELETE {baseUrl}{revokePath}{resultId}
headers: Authorization: Bearer <delegation JWT>
```

- `revokePath` 来自工具的 `@KeelbaseTool(revokePath = "DELETE /api/compensation/followups/{id}")`——格式为 `METHOD path`，带 `{id}` 占位。
- `{id}` 填入副作用的 `resultId`。
- 端点必须返回 **2xx** 表示成功，且**幂等**（重复调用返回同样结果、不报错）。

## 2. 脚手架

继承 `KeelBaseCompensationSupport<T>`，实现一个端点：

```java
@RestController
@RequestMapping("/api")
public class FollowupController extends KeelBaseCompensationSupport<Map<String, Object>> {

    public FollowupController(RevocationLedgerStore ledger, CompensationAuditSink auditSink) {
        super(ledger, auditSink);
    }

    @DeleteMapping("/compensation/followups/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
        return handleRevoke(request, id,
                store::get,                                   // finder：resultId → 实体（null 视为已不存在）
                (item, subject) -> item.put("cancelled", true), // cancelOp：软删 / 补偿
                "compensation.followups.revoke");             // 审计动作标识
    }
}
```

`handleRevoke` 依次执行：

1. **身份** —— 必须有委托 principal（否则 401 `delegation.missing`）。
2. **幂等** —— 若 `resultId` 已撤销或 `finder` 返回 null，直接返回 `200 {idempotent:true, resultId}`，不动数据。
3. **撤销** —— 调用 `cancelOp(entity, subject)`。
4. **记账** —— 标记已撤销并写审计。
5. **响应** —— `200 {idempotent:false, resultId, status:"revoked"}`。

## 3. 响应语义

| 响应 | 含义 |
|---|---|
| 2xx | 撤销被接受。`idempotent:false` = 首次；`idempotent:true` = 已撤销/未知 id。 |
| 非 2xx | KeelBase 判定撤销失败并透传原因——硬删抛错、锁冲突等对运维可见。 |

## 4. 幂等账本

默认 `RevocationLedgerStore` 是 `RevocationLedger`：进程内 LRU（上限 `keelbase.compensation.ledger-size`，缺省 1024）。**单实例**下正确。

**多实例 / 高可用**：实现 `RevocationLedgerStore` 接持久化存储，注册为 bean 覆盖默认：

```java
@Bean
RevocationLedgerStore revocationLedger(JdbcTemplate jdbc) {
    return new JdbcRevocationLedger(jdbc); // 你的实现
}
```

接口极简：`boolean markRevoked(long resultId)` 与 `boolean isRevoked(long resultId)`。建议以你的行状态为最终依据，账本只做快速路径防重。

## 5. 审计

默认 `CompensationAuditSink` 打到 SLF4J。要写入自己的审计表，实现接口并注册 bean：

```java
@Bean
CompensationAuditSink auditSink(AuditRepository audits) {
    return (action, resultId, subject) ->
        audits.save(new CompensationAudit(action, resultId, subject, Instant.now()));
}
```

签名带上委托身份，与 KeelBase 的审计哈希链呼应，「谁在何时撤销了什么」端到端可追溯。

## 6. 软删 vs 硬删

建议**软删**（`cancelled` 标记，如示例）：保留行供审计，且 `finder` 仅在行真正消失时才返回 null。必须硬删时在事务里做，让异常向上抛（非 2xx → 运维可见的失败）。

## 7. KeelBase 侧撤销入口

- **管理台** → AI 工具与副作用 → 撤销按钮（admin）。
- **工作台 / AI 执行轨迹** → 本人撤销（行为用户）。
- 只有声明了 `revokePath` 的工具支持外部撤销；其余返回诚实的「无本地补偿端点」提示。
