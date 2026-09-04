// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.approvalsample;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reference Project 验收：decide_approval_request 委托身份写回 + 撤销走补偿端点（幂等 + 401 fail-closed）。
 * 对齐 CRM/PM reference——覆盖「携身份治理」写回 + 决策→撤销→恢复待审全生命周期。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApprovalCompensationTest {

    private static final String SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";
    private static final String AUDIENCE = "legacy-approval";

    @Autowired
    private MockMvc mvc;

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
    void decide_withDelegation_writesDecidedBy_thenRevokeRestoresPending() throws Exception {
        // 委托身份写回：approve 小额（800 ≤ 阈值）→ auto_approved + decidedBy = token 的 oidcSub
        mvc.perform(patch("/api/approval-requests/1/decision")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true,\"needsReview\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("auto_approved"))
                .andExpect(jsonPath("$.decidedBy").value("oidc-user-1"));

        // 撤销走补偿端点 → 恢复待审 + 清 decidedBy
        mvc.perform(delete("/api/compensation/approval-decisions/1")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotent").value(false));

        mvc.perform(delete("/api/compensation/approval-decisions/1")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotent").value(true));

        mvc.perform(get("/api/approval-requests/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.decidedBy").doesNotExist());
    }

    @Test
    void revoke_withoutToken_401_missing() throws Exception {
        mvc.perform(delete("/api/compensation/approval-decisions/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("delegation.missing"));
    }
}
