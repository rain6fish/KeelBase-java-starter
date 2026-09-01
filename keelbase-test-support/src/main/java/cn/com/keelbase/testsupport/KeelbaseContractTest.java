package cn.com.keelbase.testsupport;

// SPDX-License-Identifier: Apache-2.0

import cn.com.keelbase.delegation.DelegationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接入合规契约测试基类——你的 {@code @SpringBootTest} 测试类继承它，即可在自家 CI 里断言
 * 「KeelBase 接入契约」（对应仓库 Node 脚本 {@code verify-java-local.mjs}）：
 *
 * <ol>
 *   <li>{@link #exportContract()} — {@code GET /keelbase/proxy-tools/export}：工具非空、
 *       audience 与 {@code keelbase.delegation.audience} 一致、写工具必带 {@code revokePath}；</li>
 *   <li>{@link #protectedPathRejectsMissingToken()} — 受保护补偿路径无 Authorization → 401；</li>
 *   <li>{@link #protectedPathAcceptsDelegatedToken()} — 携带共享密钥签发的委托 JWT → 2xx 幂等。</li>
 * </ol>
 *
 * <pre>
 * &#64;SpringBootTest
 * class MyContractComplianceTest extends KeelbaseContractTest {
 * }
 * </pre>
 *
 * <p>基类注入 {@link MockMvc}（{@link AutoConfigureMockMvc}）、{@link DelegationProperties}（读取
 * 共享 secret/audience 构造委托 JWT）。要求导出端点开启（{@code keelbase.tools.export-enabled=true}）
 * 且至少声明一个带 {@code revokePath} 的写工具。
 */
@AutoConfigureMockMvc
public abstract class KeelbaseContractTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected DelegationProperties delegation;

    @Autowired
    protected ObjectMapper mapper;

    /** ① 导出契约：工具非空、audience 一致、存在可撤销的写工具（revokePath 能力）。 */
    @Test
    void exportContract() throws Exception {
        JsonNode cfg = fetchExport();
        JsonNode tools = cfg.path("tools");
        assertTrue(tools.isArray() && !tools.isEmpty(),
                "导出应至少有一个 @KeelbaseTool 工具");
        assertEquals(delegation.getAudience(), cfg.path("audience").asText(),
                "导出 audience 应等于 keelbase.delegation.audience");
        boolean hasRevocableWrite = false;
        for (JsonNode t : tools) {
            if (isWrite(t.path("method").asText())
                    && !t.path("revokePath").asText("").isBlank()) {
                hasRevocableWrite = true;
                break;
            }
        }
        assertTrue(hasRevocableWrite,
                "导出应至少有一个带 revokePath 的写工具（AI 写副作用可撤销）——纯读或不可撤销的写工具面不满足治理要求");
    }

    /** ② 受保护补偿路径：无 Authorization → 401（delegation.missing）。 */
    @Test
    void protectedPathRejectsMissingToken() throws Exception {
        String[] revoke = firstRevokeEndpoint();
        mockMvc.perform(request(HttpMethod.valueOf(revoke[0]), revoke[1]))
                .andExpect(status().isUnauthorized());
    }

    /** ③ 受保护补偿路径：携带委托 JWT → 2xx 幂等（验签通过放行）。 */
    @Test
    void protectedPathAcceptsDelegatedToken() throws Exception {
        String[] revoke = firstRevokeEndpoint();
        MvcResult res = mockMvc.perform(request(HttpMethod.valueOf(revoke[0]), revoke[1])
                        .header("Authorization", "Bearer " + delegationToken()))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        assertNotNull(res.getResponse().getContentAsString());
    }

    /** 用共享 DELEGATION_SECRET 构造委托 JWT（HS256，sub/aud/iss/exp 对齐 DelegationAuthFilter）。 */
    protected String delegationToken() {
        String secret = delegation.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("keelbase.delegation.secret 未配置——契约测试需共享 DELEGATION_SECRET");
        }
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject("local:42")
                .setAudience(delegation.getAudience())
                .setIssuer("keelbase")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private JsonNode fetchExport() throws Exception {
        MvcResult res = mockMvc.perform(request(HttpMethod.GET, "/keelbase/proxy-tools/export"))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString());
    }

    /** 从导出找第一个带 revokePath 的写工具，拆成 {method, path}（{id} 占位替换为 123）。 */
    private String[] firstRevokeEndpoint() throws Exception {
        JsonNode cfg = fetchExport();
        for (JsonNode t : cfg.path("tools")) {
            String revoke = t.path("revokePath").asText("");
            if (!revoke.isBlank()) {
                int sp = revoke.indexOf(' ');
                String method = revoke.substring(0, sp).trim();
                String path = revoke.substring(sp + 1).trim().replace("{id}", "123");
                return new String[]{method, path};
            }
        }
        throw new IllegalStateException("导出中没有带 revokePath 的写工具——契约测试需要至少一个可撤销写工具");
    }

    private static boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }
}
