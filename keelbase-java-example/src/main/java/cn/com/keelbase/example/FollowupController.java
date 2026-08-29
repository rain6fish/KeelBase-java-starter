package cn.com.keelbase.example;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 跟进任务（followup）示例资源：演示 @KeelbaseTool 声明 AI 工具 + 补偿脚手架。
 *
 * <p>写端点声明的 {@code revokePath} 指向本类的补偿端点（继承 {@link KeelBaseCompensationSupport}
 * 获得幂等 + 审计 + 委托身份校验）；KeelBase 撤销 AI 副作用时以委托身份调用它。
 */
@RestController
@RequestMapping("/api")
public class FollowupController extends KeelBaseCompensationSupport<Map<String, Object>> {

    private final Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public FollowupController(RevocationLedgerStore ledger, CompensationAuditSink auditSink) {
        super(ledger, auditSink);
    }

    @GetMapping("/followups")
    @KeelbaseTool(name = "list_followups", description = "列出全部跟进任务（读工具，R1 自动）")
    public List<Map<String, Object>> list() {
        return new ArrayList<>(store.values());
    }

    @PostMapping("/followups")
    @KeelbaseTool(
            name = "create_followup",
            description = "创建跟进任务（写工具，R3 需人工确认；可撤销）",
            revokePath = "DELETE /api/compensation/followups/{id}")
    public Map<String, Object> create(@RequestBody FollowupRequest req,
                                      @DelegationUser DelegationPrincipal principal) {
        long id = idSeq.getAndIncrement();
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("content", req.content());
        item.put("customerId", req.customerId());
        item.put("priority", req.priority() == null ? null : req.priority().name());
        item.put("createdBy", principal == null ? "anonymous" : principal.identity());
        store.put(id, item);
        return item;
    }

    /**
     * 查询端点：演示继承 DTO 参数提取（父类 page/limit 导出、@JsonIgnore 不导出）。
     * 写方法 → 默认 R3 需确认（KeelBase 侧）。
     */
    @PostMapping("/followups/search")
    @KeelbaseTool(name = "search_followups",
            description = "按关键词查询跟进任务（演示继承 DTO 参数提取：父类字段导出，@JsonIgnore 不导出）")
    public List<Map<String, Object>> search(@RequestBody FollowupQuery query) {
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().toLowerCase();
        List<Map<String, Object>> hits = new ArrayList<>();
        for (Map<String, Object> item : store.values()) {
            String content = String.valueOf(item.get("content")).toLowerCase();
            if (keyword.isBlank() || content.contains(keyword)) {
                hits.add(item);
            }
        }
        return hits;
    }

    /**
     * 补偿端点：KeelBase 撤销 AI 创建的跟进任务时调用（委托身份 + 幂等 + 审计由基类处理）。
     * DelegationAuthFilter 保护（paths 含 /api/compensation）。
     */
    @DeleteMapping("/compensation/followups/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, HttpServletRequest request) {
        return handleRevoke(request, id,
                store::get,
                (item, subject) -> {
                    item.put("cancelled", true);
                    item.put("cancelledBy", subject);
                },
                "compensation.followups.revoke");
    }
}
