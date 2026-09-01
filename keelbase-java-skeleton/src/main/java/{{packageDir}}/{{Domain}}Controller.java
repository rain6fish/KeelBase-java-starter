// SPDX-License-Identifier: Apache-2.0

package {{packagePath}};

import cn.com.keelbase.annotation.KeelbaseTool;
import cn.com.keelbase.compensation.CompensationAuditSink;
import cn.com.keelbase.compensation.KeelBaseCompensationSupport;
import cn.com.keelbase.compensation.RevocationLedgerStore;
import cn.com.keelbase.delegation.DelegationPrincipal;
import cn.com.keelbase.delegation.DelegationUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 骨架业务控制器：两个 {@code @KeelbaseTool} 工具（读 R1 自动 / 写 R3 需确认 + 可撤销）
 * + KeelBase 撤销 AI 副作用时调用的补偿端点。业务逻辑用内存 Map 演示——换成你的 Service/DB 即可。
 */
@RestController
@RequestMapping("/api/items")
public class {{Domain}}Controller extends KeelBaseCompensationSupport<Map<String, Object>> {

    private final Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public {{Domain}}Controller(RevocationLedgerStore ledger, CompensationAuditSink auditSink) {
        super(ledger, auditSink);
    }

    // 读工具 → R1 自动执行
    @GetMapping("/items")
    @KeelbaseTool(name = "list_items", description = "列出条目（读，R1 自动）")
    public List<Map<String, Object>> list() {
        return new ArrayList<>(store.values());
    }

    // 写工具 → R3 需人工确认；可撤销
    @PostMapping("/items")
    @KeelbaseTool(name = "create_item", description = "创建条目（写，R3 需确认）",
            revokePath = "DELETE /api/compensation/items/{id}")
    public Map<String, Object> create(@RequestBody CreateItemRequest req,
                                      @DelegationUser DelegationPrincipal principal) {
        long id = idSeq.getAndIncrement();
        Map<String, Object> item = new ConcurrentHashMap<>();
        item.put("id", id);
        item.put("title", req.title());
        item.put("createdBy", principal == null ? "anonymous" : principal.identity());
        store.put(id, item);
        return item;
    }

    // KeelBase 撤销 AI 副作用时调用的补偿端点（幂等）
    @DeleteMapping("/compensation/items/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
        return handleRevoke(request, id, store::get,
                (item, subject) -> item.put("cancelled", true),
                "compensation.items.revoke");
    }
}
