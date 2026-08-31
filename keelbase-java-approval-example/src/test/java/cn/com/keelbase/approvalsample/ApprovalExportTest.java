package cn.com.keelbase.approvalsample;

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

/** 审批参考项目验收：导出与 KeelBase ai_proxy_tools 契约对齐 + 补偿端点幂等撤销。 */
@SpringBootTest
@AutoConfigureMockMvc
class ApprovalExportTest {

    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";
    private static final String AUDIENCE = "legacy-approval";

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
    void export_containsApprovalTools_withAlignedContract() throws Exception {
        MvcResult res = mvc.perform(get("/keelbase/proxy-tools/export"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = mapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertEquals("http://localhost:8084", root.get("baseUrl").asText());
        assertEquals("legacy-approval", root.get("audience").asText());

        JsonNode tools = root.get("tools");
        JsonNode query = null;
        JsonNode decide = null;
        for (JsonNode t : tools) {
            String n = t.get("name").asText();
            if ("query_approval_requests".equals(n)) {
                query = t;
            }
            if ("decide_approval_request".equals(n)) {
                decide = t;
            }
        }
        assertTrue(query != null, "应包含 query_approval_requests 读工具");
        assertEquals("GET", query.get("method").asText());
        assertEquals("R1", query.get("riskLevel").asText());

        assertTrue(decide != null, "应包含 decide_approval_request 写工具");
        assertEquals("PATCH", decide.get("method").asText());
        assertEquals("R3", decide.get("riskLevel").asText());
        assertEquals("DELETE /api/compensation/approval-decisions/{id}", decide.get("revokePath").asText());
    }

    @Test
    void compensation_revokeDecision_idempotent() throws Exception {
        // 不存在/已撤销的决定 → 200 {idempotent:true}（幂等撤销）
        MvcResult res = mvc.perform(delete("/api/compensation/approval-decisions/99999")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(body.get("idempotent").asBoolean(), "撤销不存在的审批决定应幂等成功");
    }
}
