package cn.com.keelbase.crmsample;

import cn.com.keelbase.annotation.KeelbaseTool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * CRM 分析工具（<b>类级 {@code @KeelbaseTool} 标注示例</b>）：
 * controller 上标一个注解，所有映射方法一键工具化（名称 = 方法名 camelCase → snake_case），
 * 个别方法用 {@code @KeelbaseTool(enabled=false)} 排除（如辅助/内部端点）。
 */
@RestController
@RequestMapping("/api/insights")
@KeelbaseTool(description = "CRM 分析工具（类级标注示例：controller 所有映射方法一键工具化，读 R1）")
public class CrmInsightsController {

    private final CrmStore store;

    public CrmInsightsController(CrmStore store) {
        this.store = store;
    }

    @GetMapping("/summary")
    public Map<String, Object> getCrmSummary() {
        long overdue = store.orders.values().stream().filter(o -> "OVERDUE".equals(o.status())).count();
        return Map.of(
                "customers", store.customers.size(),
                "orders", store.orders.size(),
                "overdueOrders", overdue);
    }

    @GetMapping("/overdue-orders")
    public List<CrmOrder> listOverdueOrders() {
        return store.orders.values().stream().filter(o -> "OVERDUE".equals(o.status())).toList();
    }

    @GetMapping("/risk-customers")
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
