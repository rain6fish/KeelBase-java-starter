package cn.com.keelbase.delegation;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 委托验签过滤器：校验 KeelBase 转发请求携带的委托 JWT
 * （{@code Authorization: Bearer <token>}），验签 HS256 + audience + issuer + 过期。
 *
 * <p>策略：
 * <ul>
 *   <li><b>有 Authorization 头</b>：必须验签通过，否则 401/403（fail-closed）。</li>
 *   <li><b>无头</b>：匹配 {@code keelbase.delegation.paths} 的路径 → 401（fail-closed）；
 *       未列路径 → 放行（fail-open，不干扰非 KeelBase 流量）。</li>
 * </ul>
 *
 * <p>验签通过后把 {@link DelegationPrincipal} 写入
 * {@link #PRINCIPAL_ATTR} request attribute；若配置了 {@link KeelBaseUserMapper} 且映射成功，
 * 映射结果写入 {@link #MAPPED_USER_ATTR}。错误响应为 JSON
 * {@code {"code":"delegation.<code>","message":"..."}}。
 */
public class DelegationAuthFilter extends OncePerRequestFilter {

    /** request attribute：验签通过的 {@link DelegationPrincipal}。 */
    public static final String PRINCIPAL_ATTR = "keelbase.delegation.principal";

    /** request attribute：{@link KeelBaseUserMapper} 映射的本地用户对象（可选）。 */
    public static final String MAPPED_USER_ATTR = "keelbase.delegation.user";

    private final DelegationProperties properties;
    private final SecretKey key;
    private final KeelBaseUserMapper userMapper;

    /**
     * @param properties 委托配置（secret/audience/issuer/paths）
     * @param userMapper 身份映射 SPI（可传默认实现）
     * @throws IllegalStateException secret 缺失或不足 32 字节（HS256 硬要求），或 audience 缺失
     */
    public DelegationAuthFilter(DelegationProperties properties, KeelBaseUserMapper userMapper) {
        this.properties = properties;
        this.userMapper = userMapper;
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "keelbase.delegation.secret 未配置：必须与 KeelBase 的 DELEGATION_SECRET 一致（≥32 字节 HS256）");
        }
        try {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "keelbase.delegation.secret 无效（HS256 需 ≥32 字节）: " + e.getMessage(), e);
        }
        String audience = properties.getAudience();
        if (audience == null || audience.isBlank()) {
            throw new IllegalStateException(
                    "keelbase.delegation.audience 未配置：必须等于 KeelBase ai_proxy_tools 顶层 audience（目标系统标识）");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        boolean protectedPath = properties.getPaths().stream()
                .anyMatch(p -> p != null && !p.isEmpty() && matchesSegment(request.getRequestURI(), p));

        if (auth == null || auth.isBlank()) {
            if (protectedPath) {
                writeError(response, 401, "delegation.missing", "缺少委托 Authorization 头");
            } else {
                chain.doFilter(request, response);
            }
            return;
        }

        if (!auth.startsWith("Bearer ")) {
            writeError(response, 401, "delegation.invalid", "Authorization 头格式应为 Bearer <token>");
            return;
        }
        String token = auth.substring("Bearer ".length()).trim();

        try {
            Jws<Claims> jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            Claims claims = jws.getBody();

            String aud = claims.getAudience();
            if (properties.getAudience() == null || !properties.getAudience().equals(aud)) {
                writeError(response, 403, "delegation.audience_mismatch",
                        "audience 不匹配（期望 " + properties.getAudience() + "）");
                return;
            }
            String expectedIssuer = properties.getIssuer();
            String actualIssuer = claims.getIssuer();
            if (expectedIssuer != null && !expectedIssuer.isBlank()
                    && !expectedIssuer.equals(actualIssuer)) {
                writeError(response, 403, "delegation.issuer_mismatch",
                        "issuer 不匹配（期望 " + expectedIssuer + "）");
                return;
            }

            String sub = claims.getSubject();
            String oidcSub = claims.get("oidcSub", String.class);
            DelegationPrincipal principal =
                    new DelegationPrincipal(sub, oidcSub, aud, actualIssuer, claims);

            request.setAttribute(PRINCIPAL_ATTR, principal);
            userMapper.map(principal).ifPresent(u -> request.setAttribute(MAPPED_USER_ATTR, u));

            chain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            writeError(response, 401, "delegation.expired", "委托 token 已过期");
        } catch (JwtException | IllegalArgumentException e) {
            writeError(response, 401, "delegation.invalid", "委托 token 验签失败");
        }
    }

    /**
     * 受保护路径前缀的<b>段边界</b>匹配：`/api/compensation` 命中自身与 `/api/compensation/...`，
     * 但<b>不</b>命中 `/api/compensations` 这类同前缀但不跨段的路径（避免误保护无关端点）。
     */
    private static boolean matchesSegment(String uri, String prefix) {
        return uri.startsWith(prefix)
                && (uri.length() == prefix.length() || uri.charAt(prefix.length()) == '/');
    }

    /** 写 JSON 错误体：{@code {"code":..., "message":...}}。 */
    static void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (status == 401) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\""
                + (message == null ? "" : message.replace("\"", "\\\"")) + "\"}");
    }
}
