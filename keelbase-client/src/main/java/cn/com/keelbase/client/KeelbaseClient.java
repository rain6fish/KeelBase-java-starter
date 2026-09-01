// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * KeelBase 客户端：委托 token 生命周期管理（AI Bridge §5 身份桥接）。
 *
 * <p>用 KeelBase 用户 JWT 换取短期委托 token（audience 限定），按剩余有效期缓存预刷新；
 * 也可本地验签委托 JWT（共享 {@code DELEGATION_SECRET}）。典型场景：Java 会话持有
 * KeelBase 用户 JWT 时，代表该用户在目标系统间以委托身份操作 / 回调。
 *
 * <p>HTTP 用 JDK 17 {@link HttpClient}（零额外依赖）；响应按 KeelBase 统一响应包装
 * （{@code { code, message, data, timestamp }}）解包。
 */
public class KeelbaseClient {

    private static final Logger log = LoggerFactory.getLogger(KeelbaseClient.class);

    private static final String DELEGATION_TOKEN_PATH = "/api/v1/auth/delegation-token";

    /** 缓存预刷新阈值（秒）：剩余 ≤30s 视为即将过期，重新获取。 */
    private static final int REFRESH_THRESHOLD_SECONDS = 30;

    private final KeelbaseClientProperties properties;
    private final String delegationSecret;
    private final String delegationAudience;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final ConcurrentMap<String, KeelbaseTokenIssue> cache = new ConcurrentHashMap<>();

    /**
     * @param properties        客户端配置（base-url/audience/超时）
     * @param delegationSecret  与 KeelBase 共享的 DELEGATION_SECRET（本地验签用，可空）
     * @param delegationAudience keelbase.delegation.audience（audience 第二回退源，可空）
     */
    public KeelbaseClient(KeelbaseClientProperties properties, String delegationSecret, String delegationAudience) {
        this.properties = properties;
        this.delegationSecret = delegationSecret;
        this.delegationAudience = delegationAudience;
        this.mapper = new ObjectMapper();
        this.http = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
    }

    /** base-url 已配置时才能发起委托 token 获取；否则仅本地验签可用。 */
    public boolean canObtain() {
        return properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank();
    }

    /**
     * 用 KeelBase 用户 JWT 获取短期委托 token。
     *
     * @param keelbaseJwt KeelBase 用户 access token
     * @param audience    目标系统标识；null/blank 时按配置回退（client.audience → delegation.audience）
     * @param ttlSeconds  有效期（秒，60-3600）；null 用 KeelBase 默认 300
     */
    public KeelbaseTokenIssue obtain(String keelbaseJwt, String audience, Integer ttlSeconds) {
        if (!canObtain()) {
            throw new KeelbaseClientException("keelbase.client.base-url 未配置，无法获取委托 token");
        }
        if (keelbaseJwt == null || keelbaseJwt.isBlank()) {
            throw new IllegalArgumentException("keelbaseJwt 必填（KeelBase 用户 access token）");
        }
        String aud = resolveAudience(audience);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(properties.getBaseUrl()) + DELEGATION_TOKEN_PATH))
                    .timeout(properties.getReadTimeout())
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + keelbaseJwt)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(aud, ttlSeconds), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new KeelbaseClientException("获取委托 token HTTP " + response.statusCode() + ": " + response.body());
            }
            return parseIssue(response.body(), aud);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeelbaseClientException("获取委托 token 被中断", e);
        } catch (IOException e) {
            throw new KeelbaseClientException("获取委托 token 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取并缓存：同一 (keelbaseJwt, audience) 复用缓存，剩余有效期 ≤30s 时预刷新（并发安全）。
     */
    public String obtainAndCache(String keelbaseJwt, String audience, Integer ttlSeconds) {
        String aud = resolveAudience(audience);
        String key = keelbaseJwt + "|" + aud;
        KeelbaseTokenIssue cached = cache.get(key);
        if (cached != null && cached.expiresIn() > REFRESH_THRESHOLD_SECONDS) {
            return cached.token();
        }
        KeelbaseTokenIssue fresh = obtain(keelbaseJwt, aud, ttlSeconds);
        cache.put(key, fresh);
        return fresh.token();
    }

    /** 本地验签委托 JWT：HS256 + audience（共享 DELEGATION_SECRET）。 */
    public void verify(String token, String expectedAudience) {
        parse(token, expectedAudience);
    }

    /**
     * 验签并返回解析的身份信息（subject / oidcSub / audience / expiresAt），
     * 供校验通过后的业务逻辑使用（如把 oidcSub 映射为本地用户）。
     */
    public Map<String, Object> verifyAndGet(String token, String expectedAudience) {
        Claims c = parse(token, expectedAudience).getBody();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("subject", c.getSubject());
        info.put("audience", c.getAudience());
        if (c.get("oidcSub") != null) {
            info.put("oidcSub", c.get("oidcSub"));
        }
        info.put("expiresAt", c.getExpiration());
        return info;
    }

    private Jws<Claims> parse(String token, String expectedAudience) {
        if (delegationSecret == null || delegationSecret.isBlank()) {
            throw new KeelbaseClientException("keelbase.delegation.secret 未配置，无法本地验签委托 token");
        }
        SecretKey key = Keys.hmacShaKeyFor(delegationSecret.getBytes(StandardCharsets.UTF_8));
        Jws<Claims> jws;
        try {
            jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new KeelbaseClientException("委托 token 验签失败", e);
        }
        if (expectedAudience != null && !expectedAudience.isBlank()
                && !expectedAudience.equals(jws.getBody().getAudience())) {
            throw new KeelbaseClientException("委托 token audience 不匹配（期望 " + expectedAudience + "）");
        }
        return jws;
    }

    private String resolveAudience(String audience) {
        if (audience != null && !audience.isBlank()) {
            return audience;
        }
        if (properties.getAudience() != null && !properties.getAudience().isBlank()) {
            return properties.getAudience();
        }
        if (delegationAudience != null && !delegationAudience.isBlank()) {
            return delegationAudience;
        }
        throw new IllegalArgumentException("audience 必填（参数、keelbase.client.audience 或 keelbase.delegation.audience）");
    }

    private String requestBody(String aud, Integer ttlSeconds) throws JsonProcessingException {
        return mapper.createObjectNode()
                .put("audience", aud)
                .put("ttlSeconds", ttlSeconds == null ? 300 : ttlSeconds)
                .toString();
    }

    private KeelbaseTokenIssue parseIssue(String body, String aud) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isObject()) {
                throw new KeelbaseClientException("委托 token 响应缺少 data: " + body);
            }
            return new KeelbaseTokenIssue(
                    data.path("token").asText(),
                    data.path("subject").asText(),
                    data.path("expiresIn").asInt(),
                    data.path("userId").asText(),
                    aud);
        } catch (IOException e) {
            throw new KeelbaseClientException("委托 token 响应解析失败: " + e.getMessage(), e);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.replaceAll("/+$", "");
    }
}
