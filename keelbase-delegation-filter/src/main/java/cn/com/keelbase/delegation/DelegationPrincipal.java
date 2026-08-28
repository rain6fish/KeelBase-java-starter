package cn.com.keelbase.delegation;

import java.util.Map;

/**
 * 验签通过后的委托身份。由 {@link DelegationAuthFilter} 写入 request attribute
 * （{@link DelegationAuthFilter#PRINCIPAL_ATTR}），控制器可用 {@code @DelegationUser}
 * 参数注入。
 *
 * <p>对应 KeelBase 委托 JWT payload：{@code sub}（oidcSub 或 {@code local:<userId>}）、
 * {@code oidcSub}、{@code aud}、{@code iss}。
 */
public record DelegationPrincipal(
        String subject,
        String oidcSub,
        String audience,
        String issuer,
        Map<String, Object> claims) {

    /** oidcSub 优先（统一身份源），否则剥掉 {@code local:} 前缀返回本地用户标识。 */
    public String identity() {
        if (oidcSub != null && !oidcSub.isBlank()) {
            return oidcSub;
        }
        if (subject != null && subject.startsWith("local:")) {
            return subject.substring("local:".length());
        }
        return subject;
    }
}
