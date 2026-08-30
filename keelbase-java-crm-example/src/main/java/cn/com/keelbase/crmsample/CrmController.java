package cn.com.keelbase.crmsample;

import cn.com.keelbase.annotation.KeelbaseTool;
import cn.com.keelbase.compensation.CompensationAuditSink;
import cn.com.keelbase.compensation.KeelBaseCompensationSupport;
import cn.com.keelbase.compensation.RevocationLedgerStore;
import cn.com.keelbase.delegation.DelegationPrincipal;
import cn.com.keelbase.delegation.DelegationUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 存量 Java CRM 控制器：把 CRM 业务端点声明为治理 AI 工具（域对齐
 * {@code specs/external-crm.openapi.json}）。
 *
 * <p>读 GET=R1 自动；写 POST/PATCH=R3 需人工确认；create_followup_task 可撤销
 * （revokePath → 本类补偿端点，继承 {@link KeelBaseCompensationSupport} 得幂等+审计+委托身份）。
 */
@RestController
@RequestMapping("/api")
public class CrmController extends KeelBaseCompensationSupport<FollowupTask> {

    private final Map<Long, CrmCustomer> customers = new ConcurrentHashMap<>();
    private final Map<Long, CrmOrder> orders = new ConcurrentHashMap<>();
    private final Map<Long, FollowupTask> followups = new ConcurrentHashMap<>();
    private final AtomicLong customerId = new AtomicLong(1);
    private final AtomicLong orderId = new AtomicLong(1);
    private final AtomicLong taskId = new AtomicLong(1);

    public CrmController(RevocationLedgerStore ledger, CompensationAuditSink auditSink) {
        super(ledger, auditSink);
        seed();
    }

    private void seed() {
        // 客户：一个活跃、一个逾期风险（供 AI 风险分析演示）
        long a = customerId.getAndIncrement();
        long b = customerId.getAndIncrement();
        customers.put(a, new CrmCustomer(a, "蓝湾地产", "BlueBay Real Estate", "contact@bluebay.cn", "ACTIVE"));
        customers.put(b, new CrmCustomer(b, "天穹科技", "SkyTech", "ops@skytech.cn", "RISK"));
        orders.put(orderId.getAndIncrement(), new CrmOrder(1, a, 120000, "PAID", "2026-07-01"));
        orders.put(orderId.getAndIncrement(), new CrmOrder(2, a, 56000, "OVERDUE", "2026-06-15"));
        orders.put(orderId.getAndIncrement(), new CrmOrder(3, b, 980000, "OVERDUE", "2026-05-30"));
    }

    // ---- 读工具（R1 自动）----

    @GetMapping("/customers")
    @KeelbaseTool(name = "list_customers", description = "客户列表（读工具，R1 自动），可按名称/公司关键字筛选")
    public List<CrmCustomer> listCustomers(@RequestParam(required = false) String keyword) {
        List<CrmCustomer> all = new ArrayList<>(customers.values());
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.toLowerCase();
            return all.stream()
                    .filter(c -> c.name().toLowerCase().contains(k)
                            || (c.company() != null && c.company().toLowerCase().contains(k)))
                    .toList();
        }
        return all.stream().sorted(Comparator.comparingLong(CrmCustomer::id)).toList();
    }

    @GetMapping("/customers/{id}")
    @KeelbaseTool(name = "get_customer", description = "客户详情（读工具，R1 自动）")
    public CrmCustomer getCustomer(@PathVariable long id) {
        CrmCustomer c = customers.get(id);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
        return c;
    }

    @GetMapping("/customers/{id}/orders")
    @KeelbaseTool(name = "list_customer_orders", description = "客户订单列表（读工具，R1 自动）——逾期/金额风险分析依据")
    public List<CrmOrder> listCustomerOrders(@PathVariable long id) {
        return orders.values().stream()
                .filter(o -> o.customerId() == id)
                .sorted(Comparator.comparingLong(CrmOrder::id))
                .toList();
    }

    // ---- 写工具（R3 需人工确认）----

    @PostMapping("/customers/{id}/followups")
    @KeelbaseTool(name = "create_followup_task",
            description = "创建跟进任务（写工具，R3 需人工确认；可撤销）",
            revokePath = "DELETE /api/compensation/followups/{id}")
    public FollowupTask createFollowupTask(@PathVariable long id,
                                           @RequestBody CreateFollowupRequest req,
                                           @DelegationUser DelegationPrincipal principal) {
        if (!customers.containsKey(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
        long taskIdVal = taskId.getAndIncrement();
        FollowupTask task = new FollowupTask(taskIdVal, id, req.content(), req.dueDate(),
                principal == null ? "anonymous" : principal.identity());
        followups.put(taskIdVal, task);
        return task;
    }

    @PatchMapping("/customers/{id}/orders/{orderId}")
    @KeelbaseTool(name = "update_order_amount",
            description = "修改订单金额（高风险写，R3 需人工确认；改价不可逆，撤销走诚实语义）")
    public CrmOrder updateOrderAmount(@PathVariable long id,
                                      @PathVariable long orderId,
                                      @RequestBody UpdateAmountRequest req) {
        CrmOrder existing = orders.get(orderId);
        if (existing == null || existing.customerId() != id) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }
        CrmOrder updated = new CrmOrder(existing.id(), existing.customerId(), req.amount(),
                existing.status(), existing.dueDate());
        orders.put(orderId, updated);
        return updated;
    }

    // ---- 补偿端点（KeelBase 撤销 AI 副作用时调用，委托身份 + 幂等 + 审计）----

    @DeleteMapping("/compensation/followups/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
        return handleRevoke(request, id,
                followups::get,
                (task, subject) -> {
                    task.setCancelled(true);
                    task.setCancelledBy(subject);
                },
                "compensation.followups.revoke");
    }
}
