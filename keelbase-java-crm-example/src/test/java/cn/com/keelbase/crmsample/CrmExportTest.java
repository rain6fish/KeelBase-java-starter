package cn.com.keelbase.crmsample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reference Project 验收：导出 5 个 CRM 工具，名称/方法/path/riskLevel 对齐
 * specs/external-crm.openapi.json（同一域，Java 侧真实实现 vs B 路径 OpenAPI 代理）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CrmExportTest {

    private static final String SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode export() throws Exception {
        var res = mvc.perform(get("/keelbase/proxy-tools/export"))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode tool(JsonNode tools, String name) {
        for (JsonNode t : tools) {
            if (name.equals(t.get("name").asText())) {
                return t;
            }
        }
        return null;
    }

    @Test
    void export_hasFiveCrmTools_alignedWithSpec() throws Exception {
        JsonNode root = export();
        assertEquals("http://localhost:8082", root.get("baseUrl").asText());
        assertEquals("legacy-crm", root.get("audience").asText());

        JsonNode tools = root.get("tools");

        // 读工具 R1
        JsonNode list = tool(tools, "list_customers");
        assertNotNull(list, "应包含 list_customers");
        assertEquals("GET", list.get("method").asText());
        assertEquals("/api/customers", list.get("path").asText());
        assertEquals("R1", list.get("riskLevel").asText());

        JsonNode get = tool(tools, "get_customer");
        assertNotNull(get, "应包含 get_customer");
        assertEquals("GET", get.get("method").asText());
        assertEquals("/api/customers/{id}", get.get("path").asText());
        assertEquals("R1", get.get("riskLevel").asText());

        JsonNode orders = tool(tools, "list_customer_orders");
        assertNotNull(orders, "应包含 list_customer_orders");
        assertEquals("GET", orders.get("method").asText());
        assertEquals("/api/customers/{id}/orders", orders.get("path").asText());
        assertEquals("R1", orders.get("riskLevel").asText());

        // 写工具 R3 + revokePath + 参数
        JsonNode create = tool(tools, "create_followup_task");
        assertNotNull(create, "应包含 create_followup_task");
        assertEquals("POST", create.get("method").asText());
        assertEquals("/api/customers/{id}/followups", create.get("path").asText());
        assertEquals("R3", create.get("riskLevel").asText());
        assertEquals("DELETE /api/compensation/followups/{id}", create.get("revokePath").asText());
        assertEquals(0, create.get("queryParams").size());

        JsonNode update = tool(tools, "update_order_amount");
        assertNotNull(update, "应包含 update_order_amount");
        assertEquals("PATCH", update.get("method").asText());
        assertEquals("/api/customers/{id}/orders/{orderId}", update.get("path").asText());
        assertEquals("R3", update.get("riskLevel").asText());
        assertEquals(0, update.get("queryParams").size());
    }

    @Test
    void export_writeTools_haveRequiredBodyParams() throws Exception {
        JsonNode tools = export().get("tools");

        boolean contentRequired = false;
        boolean amountRequired = false;
        for (JsonNode t : tools) {
            if ("create_followup_task".equals(t.get("name").asText())) {
                for (JsonNode p : t.get("parameters")) {
                    if ("content".equals(p.get("name").asText())) {
                        contentRequired = p.get("required").asBoolean();
                        assertEquals("string", p.get("type").asText());
                    }
                    if ("dueDate".equals(p.get("name").asText())) {
                        assertEquals("string", p.get("type").asText());
                    }
                }
            }
            if ("update_order_amount".equals(t.get("name").asText())) {
                boolean sawId = false;
                boolean sawOrderId = false;
                for (JsonNode p : t.get("parameters")) {
                    switch (p.get("name").asText()) {
                        case "id" -> sawId = true;
                        case "orderId" -> sawOrderId = true;
                        case "amount" -> {
                            amountRequired = p.get("required").asBoolean();
                            assertEquals("number", p.get("type").asText());
                        }
                        default -> {
                        }
                    }
                }
                assertTrue(sawId, "path 参数 id 应导出");
                assertTrue(sawOrderId, "path 参数 orderId 应导出");
            }
        }
        assertTrue(contentRequired, "content（@JsonProperty(required=true)）应必填");
        assertTrue(amountRequired, "amount（@JsonProperty(required=true)）应必填");
    }

    @Test
    void export_classLevelAnnotation_toolsAllMethods_andExcludesDisabled() throws Exception {
        JsonNode tools = export().get("tools");

        // 类级 @KeelbaseTool：controller 所有映射方法一键工具化（名称 = 方法名 snake_case）
        for (String expected : new String[]{"get_crm_summary", "list_overdue_orders", "get_risk_customers"}) {
            assertNotNull(tool(tools, expected), "类级标注应导出 " + expected);
            assertEquals("R1", tool(tools, expected).get("riskLevel").asText());
        }
        assertEquals("GET", tool(tools, "get_crm_summary").get("method").asText());
        assertEquals("/api/insights/summary", tool(tools, "get_crm_summary").get("path").asText());

        // 类级标注 + springdoc：工具描述从 @Operation(summary) 自动提取（无需 @KeelbaseTool.description）
        assertEquals("CRM 汇总：客户数/订单数/逾期订单数",
                tool(tools, "get_crm_summary").get("description").asText(),
                "类级工具描述应从 @Operation(summary) 自动提取");
        assertEquals("逾期订单列表（风险分析依据）",
                tool(tools, "list_overdue_orders").get("description").asText());

        // enabled=false 的方法排除（辅助/内部端点不工具化）
        assertEquals(null, tool(tools, "internal_health"), "enabled=false 的方法不应导出");
    }

    @Test
    void export_listCustomers_paginationAndParameterDescriptions() throws Exception {
        JsonNode tools = export().get("tools");
        JsonNode list = tool(tools, "list_customers");
        assertNotNull(list);
        boolean sawKeyword = false;
        boolean sawPage = false;
        boolean sawLimit = false;
        for (JsonNode p : list.get("parameters")) {
            switch (p.get("name").asText()) {
                case "keyword" -> {
                    sawKeyword = true;
                    assertEquals("名称/公司关键字", p.get("description").asText(),
                            "@Parameter(description) 应作为参数描述");
                }
                case "page" -> {
                    sawPage = true;
                    assertEquals("integer", p.get("type").asText());
                    assertEquals("页码（从 1 起）；默认: 1", p.get("description").asText(),
                            "分页参数应透传 @Parameter + 默认值");
                    assertFalse(p.get("required").asBoolean(), "带 defaultValue 的分页参数非必填");
                }
                case "limit" -> {
                    sawLimit = true;
                    assertEquals("integer", p.get("type").asText());
                    assertEquals("每页条数（默认 20）；默认: 20", p.get("description").asText());
                    assertFalse(p.get("required").asBoolean());
                }
                default -> {
                }
            }
        }
        assertTrue(sawKeyword, "应导出 keyword 筛选参数");
        assertTrue(sawPage, "应导出 page 分页参数");
        assertTrue(sawLimit, "应导出 limit 分页参数");
    }
}
