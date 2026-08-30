package cn.com.keelbase.crmsample;

import cn.com.keelbase.annotation.KeelbaseTool;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * CRM 分析工具（<b>类级 {@code @KeelbaseTool} + springdoc 描述提取示例</b>）：
 * controller 上标一个注解，所有映射方法一键工具化（名称 = 方法名 camelCase → snake_case），
 * 工具描述从 {@code @Operation(summary)} 自动提取（classpath 有 springdoc 时），不必重复写；
 * 个别方法用 {@code @KeelbaseTool(enabled=false)} 排除（如辅助/内部端点）。
 */
@RestController
@RequestMapping("/api/insights")
@KeelbaseTool
public class CrmInsightsController {

    private final CrmStore store;

    public CrmInsightsController(CrmStore store) {
        this.store = store;
    }

    @GetMapping("/summary")
    @Operation(summary = "CRM 汇总：客户数/订单数/逾期订单数")
    public Map<String, Object> getCrmSummary() {
        long overdue = store.orders.values().stream().filter(o -> "OVERDUE".equals(o.status())).count();
        return Map.of(
                "customers", store.customers.size(),
                "orders", store.orders.size(),
                "overdueOrders", overdue);
    }

    @GetMapping("/overdue-orders")
    @Operation(summary = "逾期订单列表（风险分析依据）")
    public List<CrmOrder> listOverdueOrders() {
        return store.orders.values().stream().filter(o -> "OVERDUE".equals(o.status())).toList();
    }

    @GetMapping("/risk-customers")
    @Operation(summary = "风险客户列表（RISK 状态）")
    public List<CrmCustomer> getRiskCustomers() {
        return store.customers.values().stream().filter(c -> "RISK".equals(c.status())).toList();
    }

    /** 辅助端点：类级标注下用 {@code enabled=false} 排除（不导出为 AI 工具）。 */
    @GetMapping("/internal/health")
    @KeelbaseTool(enabled = false)
    public Map<String, String> internalHealth() {
        return Map.of("status", "UP");
    }
}
