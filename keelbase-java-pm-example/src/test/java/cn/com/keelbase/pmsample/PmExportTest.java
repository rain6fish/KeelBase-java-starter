package cn.com.keelbase.pmsample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PM 参考项目验收：导出与 KeelBase ai_proxy_tools 契约对齐 + 补偿端点幂等撤销。 */
@SpringBootTest
@AutoConfigureMockMvc
class PmExportTest {

    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";
    private static final String AUDIENCE = "legacy-pm";

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();

    /** 构造委托 JWT（对齐 DelegationAuthFilter 验签：HS256 + aud/iss/exp）。 */
    private String token() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject("local:42")
                .claim("oidcSub", "oidc-user-1")
                .setAudience(AUDIENCE)
                .setIssuer("keelbase")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void export_containsPmTools_withAlignedContract() throws Exception {
        MvcResult res = mvc.perform(get("/keelbase/proxy-tools/export"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = mapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertEquals("http://localhost:8083", root.get("baseUrl").asText());
        assertEquals("legacy-pm", root.get("audience").asText());

        JsonNode tools = root.get("tools");
        JsonNode query = null;
        JsonNode create = null;
        for (JsonNode t : tools) {
            String n = t.get("name").asText();
            if ("query_projects".equals(n)) {
                query = t;
            }
            if ("create_pm_task".equals(n)) {
                create = t;
            }
        }
        assertTrue(query != null, "应包含 query_projects 读工具");
        assertEquals("GET", query.get("method").asText());
        assertEquals("R1", query.get("riskLevel").asText());

        assertTrue(create != null, "应包含 create_pm_task 写工具");
        assertEquals("POST", create.get("method").asText());
        assertEquals("R3", create.get("riskLevel").asText());
        assertEquals("DELETE /api/compensation/pm-tasks/{id}", create.get("revokePath").asText());

        // 写工具 body 字段 title 展开为参数
        boolean sawTitle = false;
        for (JsonNode p : create.get("parameters")) {
            if ("title".equals(p.get("name").asText())) {
                sawTitle = true;
            }
        }
        assertTrue(sawTitle, "create_pm_task 的 body 字段 title 应展开为参数");
    }

    @Test
    void compensation_revokeTask_idempotent() throws Exception {
        // 补偿端点在受保护路径（/api/compensation）——需委托 token
        // 不存在/已撤销的任务 → 200 {idempotent:true}（幂等：撤销已不存在的副作用视为成功）
        MvcResult res = mvc.perform(delete("/api/compensation/pm-tasks/99999")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(body.get("idempotent").asBoolean(), "撤销不存在任务应幂等成功（idempotent=true）");
    }
}
