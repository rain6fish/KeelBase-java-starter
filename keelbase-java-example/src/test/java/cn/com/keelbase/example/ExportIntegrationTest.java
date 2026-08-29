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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M2 验收：GET /keelbase/proxy-tools/export 导出与 KeelBase ai_proxy_tools 契约对齐。 */
@SpringBootTest
@AutoConfigureMockMvc
class ExportIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void export_containsListAndCreateTools_withAlignedContract() throws Exception {
        MvcResult res = mvc.perform(get("/keelbase/proxy-tools/export"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = mapper.readTree(res.getResponse().getContentAsString());

        assertEquals("http://localhost:8081", root.get("baseUrl").asText());
        assertEquals("legacy-crm", root.get("audience").asText());

        JsonNode tools = root.get("tools");
        JsonNode listTool = null;
        JsonNode createTool = null;
        for (JsonNode t : tools) {
            String n = t.get("name").asText();
            if ("list_followups".equals(n)) {
                listTool = t;
            }
            if ("create_followup".equals(n)) {
                createTool = t;
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
    }
}
