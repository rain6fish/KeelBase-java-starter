// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.pmsample;

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

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reference Project 验收：create_pm_task 委托身份写回 + 撤销走补偿端点（幂等 + 401 fail-closed）。
 * 对齐 CRM reference（CrmCompensationTest）——覆盖「携身份治理」写回 + 写→撤销全生命周期。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PmCompensationTest {

    private static final String SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";
    private static final String AUDIENCE = "legacy-pm";

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
    void create_withDelegation_writesCreatedBy_thenRevokeIdempotent() throws Exception {
        var created = mvc.perform(post("/api/projects/1/tasks")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"升级订单模块依赖\"}"))
                .andExpect(status().isOk())
                // 委托身份写回：KeelBase 携身份治理 → createdBy = token 的 oidcSub
                .andExpect(jsonPath("$.createdBy").value("oidc-user-1"))
                .andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // 撤销走补偿端点：委托身份 + 幂等 + 审计
        mvc.perform(delete("/api/compensation/pm-tasks/" + id)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotent").value(false))
                .andExpect(jsonPath("$.status").value("revoked"));

        mvc.perform(delete("/api/compensation/pm-tasks/" + id)
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotent").value(true));

        // 撤销后任务 cancelled + createdBy 保留（全生命周期闭环）
        mvc.perform(get("/api/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[?(@.id == " + id + ")].cancelled").value(true))
                .andExpect(jsonPath("$.tasks[?(@.id == " + id + ")].createdBy").value("oidc-user-1"));
    }

    @Test
    void revoke_withoutToken_401_missing() throws Exception {
        mvc.perform(delete("/api/compensation/pm-tasks/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("delegation.missing"));
    }
}
