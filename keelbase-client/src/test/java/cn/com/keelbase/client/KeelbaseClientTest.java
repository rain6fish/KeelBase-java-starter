// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.client;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KeelbaseClient：obtain 解包 / audience·ttl 传参 / 缓存预刷新 / verify 验签。 */
class KeelbaseClientTest {

    private static final String SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";

    private HttpServer server;
    private final AtomicInteger tokenRequests = new AtomicInteger();
    private volatile String lastBody = "";
    private volatile String lastAuth = "";
    private volatile int tokenExpiresIn = 300;

    private KeelbaseClient client(String audience) {
        KeelbaseClientProperties props = new KeelbaseClientProperties();
        props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        props.setAudience(audience);
        return new KeelbaseClient(props, SECRET, "delegation-fallback");
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/auth/delegation-token", exchange -> {
            tokenRequests.incrementAndGet();
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
            String json = "{\"code\":200,\"message\":\"ok\",\"data\":{"
                    + "\"token\":\"delegated-jwt\",\"subject\":\"local:42\","
                    + "\"expiresIn\":" + tokenExpiresIn + ",\"userId\":\"42\",\"audience\":\"legacy-crm\""
                    + "},\"timestamp\":\"2026-08-29T00:00:00Z\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void obtain_parsesWrappedData_andSendsAudienceTtlAndBearer() {
        KeelbaseTokenIssue issue = client("legacy-crm").obtain("user-jwt", null, 600);

        assertEquals("delegated-jwt", issue.token());
        assertEquals("local:42", issue.subject());
        assertEquals(300, issue.expiresIn());
        assertEquals("42", issue.userId());
        assertEquals("legacy-crm", issue.audience());
        assertEquals("Bearer user-jwt", lastAuth);
        assertTrue(lastBody.contains("\"audience\":\"legacy-crm\""), "请求体应含 audience");
        assertTrue(lastBody.contains("\"ttlSeconds\":600"), "请求体应含 ttlSeconds");
        assertEquals(1, tokenRequests.get());
    }

    @Test
    void obtain_audienceParam_overridesConfig() {
        KeelbaseTokenIssue issue = client("configured-aud").obtain("user-jwt", "override-aud", null);
        assertEquals("override-aud", issue.audience());
    }

    @Test
    void obtainAndCache_reusesCacheForSameKey() {
        KeelbaseClient c = client("legacy-crm");
        String first = c.obtainAndCache("user-jwt", "legacy-crm", null);
        String second = c.obtainAndCache("user-jwt", "legacy-crm", null);
        assertEquals(first, second);
        assertEquals(1, tokenRequests.get(), "缓存命中应只请求一次");
    }

    @Test
    void obtainAndCache_refreshesWhenExpiring() {
        tokenExpiresIn = 20; // ≤30s 视为即将过期 → 预刷新
        KeelbaseClient c = client("legacy-crm");
        c.obtainAndCache("user-jwt", "legacy-crm", null);
        c.obtainAndCache("user-jwt", "legacy-crm", null);
        assertEquals(2, tokenRequests.get(), "剩余有效期不足应重新获取");
    }

    @Test
    void verify_validToken_passes() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .setSubject("local:42").setAudience("legacy-crm").setIssuer("keelbase")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256).compact();
        client("legacy-crm").verify(token, "legacy-crm"); // 不抛
    }

    @Test
    void verifyAndGet_returnsParsedIdentity() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .setSubject("local:42").claim("oidcSub", "oidc-user-1")
                .setAudience("legacy-crm").setIssuer("keelbase")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256).compact();
        Map<String, Object> info = client("legacy-crm").verifyAndGet(token, "legacy-crm");
        assertEquals("local:42", info.get("subject"));
        assertEquals("oidc-user-1", info.get("oidcSub"));
        assertEquals("legacy-crm", info.get("audience"));
        assertNotNull(info.get("expiresAt"));
    }

    @Test
    void verify_tamperedToken_throws() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .setSubject("local:42").setAudience("legacy-crm").setIssuer("keelbase")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256).compact();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThrows(KeelbaseClientException.class, () -> client("legacy-crm").verify(tampered, "legacy-crm"));
    }

    @Test
    void verify_wrongAudience_throws() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .setSubject("local:42").setAudience("other-system").setIssuer("keelbase")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, SignatureAlgorithm.HS256).compact();
        assertThrows(KeelbaseClientException.class, () -> client("legacy-crm").verify(token, "legacy-crm"));
    }

    @Test
    void verify_withoutSecret_throws() {
        KeelbaseClient c = new KeelbaseClient(new KeelbaseClientProperties(), null, null);
        assertThrows(KeelbaseClientException.class, () -> c.verify("any", "aud"));
    }

    @Test
    void querySideEffect_parsesStatusAndHonestHint() throws Exception {
        server.createContext("/api/v1/external/effects/proxy_call/7", exchange -> {
            assertEquals("side-key", exchange.getRequestHeaders().getFirst("x-api-key"));
            String json = "{\"code\":200,\"data\":{"
                    + "\"effect\":{\"id\":9,\"toolName\":\"proxy_create_followup\",\"userId\":3,"
                    + "\"conversationId\":null,\"resultType\":\"proxy_call\",\"resultId\":7,"
                    + "\"createdAt\":\"2026-09-01T00:00:00Z\"},"
                    + "\"target\":{\"targetExists\":false,\"targetSoftDeleted\":false,\"targetTitle\":null},"
                    + "\"revoked\":false,"
                    + "\"revokeHint\":\"B 路径外部副作用：撤销经 Java 补偿端点，撤销态需在 Java 侧确认\""
                    + "},\"timestamp\":\"2026-09-01T00:00:00Z\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        KeelbaseClientProperties props = new KeelbaseClientProperties();
        props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        props.setSideEffectApiKey("side-key");
        SideEffectStatus s = new KeelbaseClient(props, SECRET, "x").querySideEffect("proxy_call", 7);
        assertTrue(s.found());
        assertEquals(9, s.effectId());
        assertEquals("proxy_create_followup", s.toolName());
        assertEquals("proxy_call", s.resultType());
        assertFalse(s.revoked());
        assertFalse(s.targetExists());
        assertNotNull(s.revokeHint());
        assertTrue(s.revokeHint().contains("Java"));
    }

    @Test
    void querySideEffect_localEntityRevoked() throws Exception {
        server.createContext("/api/v1/external/effects/crm_task/42", exchange -> {
            String json = "{\"code\":200,\"data\":{"
                    + "\"effect\":{\"id\":21,\"toolName\":\"create_followup_task\",\"resultType\":\"crm_task\",\"resultId\":42},"
                    + "\"target\":{\"targetExists\":false,\"targetSoftDeleted\":true,\"targetTitle\":null},"
                    + "\"revoked\":true"
                    + "},\"timestamp\":\"2026-09-01T00:00:00Z\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        KeelbaseClientProperties props = new KeelbaseClientProperties();
        props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        props.setSideEffectApiKey("side-key");
        SideEffectStatus s = new KeelbaseClient(props, SECRET, "x").querySideEffect("crm_task", 42);
        assertTrue(s.found());
        assertTrue(s.revoked());
        assertTrue(s.targetSoftDeleted());
    }

    @Test
    void querySideEffect_404_returnsNotFound() throws Exception {
        server.createContext("/api/v1/external/effects/followup/99", exchange -> {
            String json = "{\"code\":404,\"message\":\"该业务动作无 AI 副作用记录\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        KeelbaseClientProperties props = new KeelbaseClientProperties();
        props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        props.setSideEffectApiKey("side-key");
        SideEffectStatus s = new KeelbaseClient(props, SECRET, "x").querySideEffect("followup", 99);
        assertFalse(s.found());
        assertEquals(0, s.effectId());
    }

    @Test
    void querySideEffect_serverError_throws() throws Exception {
        server.createContext("/api/v1/external/effects/followup/7", exchange -> {
            String json = "{\"code\":500,\"message\":\"boom\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        KeelbaseClientProperties props = new KeelbaseClientProperties();
        props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        props.setSideEffectApiKey("side-key");
        assertThrows(KeelbaseClientException.class,
                () -> new KeelbaseClient(props, SECRET, "x").querySideEffect("followup", 7));
    }

    @Test
    void querySideEffect_missingApiKey_throws() {
        KeelbaseClientProperties props = new KeelbaseClientProperties();
        props.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        assertThrows(KeelbaseClientException.class,
                () -> new KeelbaseClient(props, SECRET, "x").querySideEffect("followup", 7));
    }
}
