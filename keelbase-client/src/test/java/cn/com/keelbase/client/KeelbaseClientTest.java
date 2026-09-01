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
}
