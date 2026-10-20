// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.crmsample;

import cn.com.keelbase.export.ProxyToolsScanner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paired readings for zero-boilerplate export: {@code infer=true} against a hand-written
 * declaration, plus the reverse control ({@code infer=false}) that must lose the inference.
 *
 * <p>零样板导出的成对读数：{@code infer=true} 对「手写全量声明」，以及必须丢掉推断的反向对照
 * （{@code infer=false}）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ZeroBoilerplateExportTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProxyToolsScanner scanner;

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode export() throws Exception {
        var res = mvc.perform(get("/keelbase/proxy-tools/export"))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode tool(String name) throws Exception {
        for (JsonNode t : export().get("tools")) {
            if (name.equals(t.get("name").asText())) {
                return t;
            }
        }
        return null;
    }

    @Test
    void zeroBoilerplate_queryParamsMatchHandwrittenDeclaration() throws Exception {
        // No name attribute: the tool name is inferred from the method name (search → search).
        // 未写 name：工具名由方法名推断（search → search）。
        JsonNode zero = tool("search");
        JsonNode hand = tool("handwritten_search");
        assertNotNull(zero, "infer=true tool should be exported");
        assertNotNull(hand, "hand-written twin should be exported");

        assertEquals(2, zero.get("parameters").size(),
                "bare simple parameters are inferred as query parameters");
        assertEquals(hand.get("parameters"), zero.get("parameters"),
                "inferred parameters must equal the hand-written declaration");
        assertEquals(hand.get("description").asText(), zero.get("description").asText(),
                "tool description should come from the Javadoc summary");
        assertEquals("R1", zero.get("riskLevel").asText(),
                "governance semantics are never inferred: GET still maps to R1");
    }

    @Test
    void zeroBoilerplate_bodyRecordMatchesHandwrittenDeclaration() throws Exception {
        JsonNode zero = tool("zero_boilerplate_note");
        JsonNode hand = tool("handwritten_note");
        assertNotNull(zero);
        assertNotNull(hand);

        assertEquals(hand.get("description").asText(), zero.get("description").asText());
        assertEquals(hand.get("parameters"), zero.get("parameters"),
                "record @param Javadoc should equal @Schema(description)");
        assertEquals("note text", zero.get("parameters").get(0).get("description").asText());
        assertEquals("optional due date", zero.get("parameters").get(1).get("description").asText());
    }

    @Test
    void inferenceOff_losesBareParametersAndDescriptions() throws Exception {
        JsonNode off = tool("no_infer_search");
        assertNotNull(off);
        assertEquals(0, off.get("parameters").size(),
                "reverse control: with infer=false the bare parameters are not exported at all");
        assertEquals("GET /api/zero/search-no-infer", off.get("description").asText(),
                "reverse control: without inference the description falls back to METHOD /path");

        JsonNode note = tool("no_infer_note");
        assertNotNull(note);
        assertEquals(2, note.get("parameters").size(),
                "@RequestBody fields are exported regardless of inference");
        for (JsonNode p : note.get("parameters")) {
            assertEquals("", p.get("description").asText(),
                    "reverse control: without inference body-field descriptions stay empty");
        }
    }

    @Test
    void unInferableParameter_isReportedAsWarning() {
        var report = scanner.scanWithReport();
        assertTrue(report.warnings().stream().anyMatch(w ->
                        w.contains("ZeroBoilerplateController#legacy")
                                && w.contains("未导出为工具参数")),
                "a complex handler parameter under infer=true must be reported, not silently dropped: "
                        + report.warnings());
        assertFalse(report.skipped().stream().anyMatch(w -> w.contains("ZeroBoilerplateController#legacy")),
                "an inference warning must not fail strict mode (it is not a declaration error)");
    }
}
