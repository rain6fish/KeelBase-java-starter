package cn.com.keelbase.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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
        return mapper.readTree(res.getResponse().getContentAsString());
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
    void status_reportsDelegationExportTools_withoutSecret() throws Exception {
        MvcResult res = mvc.perform(get("/keelbase/status"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = mapper.readTree(res.getResponse().getContentAsString());

        JsonNode delegation = root.get("delegation");
        assertTrue(delegation.get("configured").asBoolean());
        assertTrue(delegation.get("secretConfigured").asBoolean());
        assertEquals("legacy-crm", delegation.get("audience").asText());
        assertEquals("keelbase", delegation.get("issuer").asText());
        assertTrue(delegation.get("protectedPaths").isArray(), "protectedPaths 应为数组");
        // 不泄露密钥明文：任何位置都不应出现 secret 值本身
        assertFalse(res.getResponse().getContentAsString().contains("0123456789012345678901234567890123456789012345678901234567890123"),
                "status 响应不得包含密钥明文");

        JsonNode export = root.get("export");
        assertTrue(export.get("enabled").asBoolean());
        assertEquals("http://localhost:8081", export.get("baseUrl").asText());
        assertEquals("legacy-crm", export.get("audience").asText());

        JsonNode tools = root.get("tools");
        assertTrue(tools.get("count").asInt() >= 3, "示例至少导出 list/create/search 三个工具");
        assertNotNull(tools.get("names"));

        assertTrue(root.get("warnings").isArray(), "warnings 应为数组");
    }
}
