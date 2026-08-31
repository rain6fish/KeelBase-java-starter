package cn.com.keelbase.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M2 验收：GET /keelbase/proxy-tools/export 导出与 KeelBase ai_proxy_tools 契约对齐；/keelbase/status 诊断。 */
@SpringBootTest
@AutoConfigureMockMvc
class ExportIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode exportJson() throws Exception {
        MvcResult res = mvc.perform(get("/keelbase/proxy-tools/export"))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void export_containsListAndCreateTools_withAlignedContract() throws Exception {
        JsonNode root = exportJson();

        // baseUrl 约定为「服务器根」（baseUrl + 完整 path 拼接），见 docs/tool-declaration.md
        assertEquals("http://localhost:8081", root.get("baseUrl").asText());
        assertEquals("legacy-crm", root.get("audience").asText());

        JsonNode tools = root.get("tools");
        JsonNode listTool = null;
        JsonNode createTool = null;
        JsonNode searchTool = null;
        for (JsonNode t : tools) {
            String n = t.get("name").asText();
            if ("list_followups".equals(n)) {
                listTool = t;
            }
            if ("create_followup".equals(n)) {
                createTool = t;
            }
            if ("search_followups".equals(n)) {
                searchTool = t;
            }
        }

        assertNotNull(listTool, "应包含 list_followups 读工具");
        assertEquals("GET", listTool.get("method").asText());
        assertEquals("/api/followups", listTool.get("path").asText());
        assertEquals("R1", listTool.get("riskLevel").asText());
        // 零样板导出：无 springdoc 描述的 @RequestParam 自动生成语义描述（customerId → customer ID（integer））
        boolean sawAutoDesc = false;
        for (JsonNode t : tools) {
            if (!"list_followups_by_customer".equals(t.get("name").asText())) {
                continue;
            }
            for (JsonNode p : t.get("parameters")) {
                if ("customerId".equals(p.get("name").asText())) {
                    assertEquals("integer", p.get("type").asText());
                    String desc = p.get("description").asText();
                    assertFalse(desc.isBlank(), "customerId 无 springdoc 描述时应有自动语义描述");
                    assertTrue(desc.toLowerCase().contains("customer"), "自动描述应含参数名语义（customer）");
                    sawAutoDesc = true;
                }
            }
        }
        assertTrue(sawAutoDesc, "list_followups_by_customer 应包含带自动描述的 customerId 参数");

        assertNotNull(createTool, "应包含 create_followup 写工具");
        assertEquals("POST", createTool.get("method").asText());
        assertEquals("/api/followups", createTool.get("path").asText());
        assertEquals("R3", createTool.get("riskLevel").asText());
        assertEquals("DELETE /api/compensation/followups/{id}", createTool.get("revokePath").asText());
        assertEquals(0, createTool.get("queryParams").size(), "写工具无 @RequestParam → queryParams 空");

        boolean contentRequired = false;
        boolean sawContent = false;
        for (JsonNode p : createTool.get("parameters")) {
            if ("content".equals(p.get("name").asText())) {
                sawContent = true;
                contentRequired = p.get("required").asBoolean();
                assertEquals("string", p.get("type").asText());
            }
            if ("customerId".equals(p.get("name").asText())) {
                assertEquals("integer", p.get("type").asText());
            }
        }
        assertTrue(sawContent, "body 字段 content 应展开为参数");
        assertTrue(contentRequired, "@JsonProperty(required=true) 应映射为 required");

        // 继承 DTO + @JsonIgnore：父类字段导出、@JsonIgnore 不导出
        assertNotNull(searchTool, "应包含 search_followups 工具（继承 DTO 演示）");
        boolean sawKeyword = false;
        boolean keywordRequired = false;
        boolean sawPage = false;
        boolean sawServerHint = false;
        for (JsonNode p : searchTool.get("parameters")) {
            switch (p.get("name").asText()) {
                case "keyword" -> {
                    sawKeyword = true;
                    keywordRequired = p.get("required").asBoolean();
                }
                case "page" -> sawPage = true;
                case "serverHint" -> sawServerHint = true;
                default -> {
                }
            }
        }
        assertTrue(sawKeyword, "keyword（@JsonProperty(required=true)）应导出");
        assertTrue(keywordRequired, "keyword 应 required");
        assertTrue(sawPage, "父类继承字段 page 应导出");
        assertFalse(sawServerHint, "@JsonIgnore 字段 serverHint 不应导出");
    }

    @Test
    void export_queryAndPatchTools_alignParameters() throws Exception {
        JsonNode root = exportJson();
        JsonNode tools = root.get("tools");
        JsonNode queryTool = null;
        JsonNode patchTool = null;
        for (JsonNode t : tools) {
            String n = t.get("name").asText();
            if ("list_followups_by_customer".equals(n)) {
                queryTool = t;
            }
            if ("mark_followup_complete".equals(n)) {
                patchTool = t;
            }
        }

        // 读工具 + @RequestParam：customerId 必填、priority 可选枚举（string）
        assertNotNull(queryTool, "应包含 list_followups_by_customer 读工具");
        assertEquals("GET", queryTool.get("method").asText());
        assertEquals("R1", queryTool.get("riskLevel").asText());
        boolean sawCustomerId = false;
        boolean customerIdRequired = false;
        boolean sawPriority = false;
        boolean priorityRequired = true;
        for (JsonNode p : queryTool.get("parameters")) {
            switch (p.get("name").asText()) {
                case "customerId" -> {
                    sawCustomerId = true;
                    customerIdRequired = p.get("required").asBoolean();
                    assertEquals("integer", p.get("type").asText());
                }
                case "priority" -> {
                    sawPriority = true;
                    priorityRequired = p.get("required").asBoolean();
                    assertEquals("string", p.get("type").asText());
                    assertEquals("可选: LOW/MEDIUM/HIGH", p.get("description").asText(),
                            "枚举 @RequestParam 应透传可选值");
                }
                default -> {
                }
            }
        }
        assertTrue(sawCustomerId, "customerId（无缺省值）应必填");
        assertTrue(customerIdRequired);
        assertTrue(sawPriority, "priority 应导出");
        assertFalse(priorityRequired, "priority(required=false) 应可选");

        // 写方法（PATCH）+ @RequestParam：path 参数 + 参数进 queryParams，R3
        assertNotNull(patchTool, "应包含 mark_followup_complete 写工具");
        assertEquals("PATCH", patchTool.get("method").asText());
        assertEquals("R3", patchTool.get("riskLevel").asText());
        boolean sawId = false;
        boolean sawDone = false;
        boolean doneRequired = true;
        for (JsonNode p : patchTool.get("parameters")) {
            switch (p.get("name").asText()) {
                case "id" -> {
                    sawId = true;
                    assertEquals("integer", p.get("type").asText());
                }
                case "done" -> {
                    sawDone = true;
                    doneRequired = p.get("required").asBoolean();
                    assertEquals("boolean", p.get("type").asText());
                    assertEquals("默认: true", p.get("description").asText(),
                            "@RequestParam(defaultValue) 应透传默认值");
                }
                default -> {
                }
            }
        }
        assertTrue(sawId, "path 参数 id 应导出");
        assertTrue(sawDone, "@RequestParam done 应导出");
        assertFalse(doneRequired, "done(defaultValue=true) 应可选");
        assertTrue(patchTool.get("queryParams").isArray());
        boolean doneInQueryParams = false;
        for (JsonNode q : patchTool.get("queryParams")) {
            if ("done".equals(q.asText())) {
                doneInQueryParams = true;
            }
        }
        assertTrue(doneInQueryParams, "写方法的 @RequestParam 应进入 queryParams");
    }

    @Test
    void status_reportsDelegationExportTools_withoutSecret() throws Exception {
        MvcResult res = mvc.perform(get("/keelbase/status"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = mapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));

        JsonNode delegation = root.get("delegation");
        assertTrue(delegation.get("configured").asBoolean());
        assertTrue(delegation.get("secretConfigured").asBoolean());
        assertEquals("legacy-crm", delegation.get("audience").asText());
        assertEquals("keelbase", delegation.get("issuer").asText());
        assertTrue(delegation.get("protectedPaths").isArray(), "protectedPaths 应为数组");
        // 不泄露密钥明文：任何位置都不应出现 secret 值本身
        assertFalse(res.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("0123456789012345678901234567890123456789012345678901234567890123"),
                "status 响应不得包含密钥明文");

        JsonNode export = root.get("export");
        assertTrue(export.get("enabled").asBoolean());
        assertEquals("http://localhost:8081", export.get("baseUrl").asText());
        assertEquals("legacy-crm", export.get("audience").asText());

        JsonNode tools = root.get("tools");
        assertTrue(tools.get("count").asInt() >= 5, "示例应导出读/写/查询/标记等 5 个以上工具");
        assertNotNull(tools.get("names"));
        // 接入健康度：风险分布 + 补偿覆盖 + overall health + errors 分级
        assertTrue(tools.get("riskDistribution").isObject(), "riskDistribution 应为对象（R1/R3 计数）");
        assertTrue(tools.get("revokeCovered").canConvertToInt(), "revokeCovered 应为整数（补偿覆盖）");
        assertTrue(tools.get("revokeCovered").asInt() >= 1, "示例写工具应配 revokePath（补偿覆盖 ≥1）");

        JsonNode health = root.get("health");
        assertNotNull(health.get("status"), "health.status 应存在");
        assertTrue(List.of("healthy", "degraded", "error").contains(health.get("status").asText()),
                "health.status 应为 healthy/degraded/error");
        assertNotNull(health.get("summary"));
        assertTrue(root.get("errors").isArray(), "errors 应为数组（阻断性问题）");
        assertTrue(root.get("warnings").isArray(), "warnings 应为数组（配置提示）");
    }
}
