package cn.com.keelbase.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M3 验收：补偿端点（委托身份 + 幂等）+ 无委托 401。 */
@SpringBootTest
@AutoConfigureMockMvc
class CompensationIntegrationTest {

    private static final String SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";
    private static final String AUDIENCE = "legacy-crm";

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();

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
    void revoke_withDelegationToken_firstRevoked_thenIdempotent() throws Exception {
        MvcResult created = mvc.perform(post("/api/followups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"a\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(delete("/api/compensation/followups/" + id)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotent").value(false))
                .andExpect(jsonPath("$.status").value("revoked"));

        mvc.perform(delete("/api/compensation/followups/" + id)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotent").value(true));
    }

    @Test
    void revoke_withoutToken_401_missing() throws Exception {
        mvc.perform(delete("/api/compensation/followups/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("delegation.missing"));
    }
}
