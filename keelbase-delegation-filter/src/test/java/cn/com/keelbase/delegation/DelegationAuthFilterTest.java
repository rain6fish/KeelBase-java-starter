package cn.com.keelbase.delegation;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M1 验收：委托验签过滤器 6 场景（合法 / 篡改 / 过期 / aud 错 / 无头放行 / 保护路径 fail-closed）。 */
class DelegationAuthFilterTest {

    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";
    private static final String AUDIENCE = "legacy-crm";

    private DelegationProperties props() {
        DelegationProperties p = new DelegationProperties();
        p.setSecret(SECRET);
        p.setAudience(AUDIENCE);
        return p;
    }

    private String token(String audience, long ttlMs) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject("local:42")
                .claim("oidcSub", "oidc-user-1")
                .setAudience(audience)
                .setIssuer("keelbase")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private MockMvc mvc(DelegationProperties p) {
        DelegationAuthFilter filter = new DelegationAuthFilter(p, new DefaultKeelBaseUserMapper());
        return MockMvcBuilders.standaloneSetup(new EchoController()).addFilters(filter).build();
    }

    @RestController
    static class EchoController {
        @GetMapping("/api/echo")
        public Map<String, Object> echo(HttpServletRequest req) {
            DelegationPrincipal p =
                    (DelegationPrincipal) req.getAttribute(DelegationAuthFilter.PRINCIPAL_ATTR);
            Object user = req.getAttribute(DelegationAuthFilter.MAPPED_USER_ATTR);
            Map<String, Object> m = new HashMap<>();
            m.put("identity", p == null ? null : p.identity());
            m.put("subject", p == null ? null : p.subject());
            m.put("user", user);
            return m;
        }
    }

    @Test
    void validToken_returns200_withPrincipalAndMappedUser() throws Exception {
        mvc(props())
                .perform(get("/api/echo").header("Authorization", "Bearer " + token(AUDIENCE, 60000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity").value("oidc-user-1"))
                .andExpect(jsonPath("$.subject").value("local:42"))
                .andExpect(jsonPath("$.user").value("oidc-user-1"));
    }

    @Test
    void tamperedToken_returns401_invalid() throws Exception {
        String t = token(AUDIENCE, 60000);
        String tampered = t.substring(0, t.length() - 4) + "AAAA";
        mvc(props())
                .perform(get("/api/echo").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("delegation.invalid"));
    }

    @Test
    void expiredToken_returns401_expired() throws Exception {
        String t = token(AUDIENCE, -60000);
        mvc(props())
                .perform(get("/api/echo").header("Authorization", "Bearer " + t))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("delegation.expired"));
    }

    @Test
    void wrongAudience_returns403_audienceMismatch() throws Exception {
        String t = token("other-system", 60000);
        mvc(props())
                .perform(get("/api/echo").header("Authorization", "Bearer " + t))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("delegation.audience_mismatch"));
    }

    @Test
    void noHeader_nonProtectedPath_passesThrough() throws Exception {
        mvc(props())
                .perform(get("/api/echo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity").doesNotExist());
    }

    @Test
    void noHeader_protectedPath_returns401_missing() throws Exception {
        DelegationProperties p = props();
        p.setPaths(List.of("/api/compensation"));
        mvc(p)
                .perform(get("/api/compensation/followups/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("delegation.missing"));
    }
}
