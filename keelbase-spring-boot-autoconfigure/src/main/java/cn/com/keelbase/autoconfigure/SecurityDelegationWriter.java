// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.autoconfigure;

import cn.com.keelbase.delegation.DelegationAuthFilter;
import cn.com.keelbase.delegation.DelegationPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 可选适配器（classpath 含 Spring Security 时装配）：把 {@link DelegationAuthFilter}
 * 验签通过的身份写入 {@link SecurityContextHolder}，供 {@code @PreAuthorize} 等使用。
 *
 * <p>在 {@code DelegationAuthFilter} 之后执行（order +1）。请求结束清理 SecurityContext，
 * 避免线程池复用泄漏身份。
 */
public class SecurityDelegationWriter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            Object principal = request.getAttribute(DelegationAuthFilter.PRINCIPAL_ATTR);
            if (principal instanceof DelegationPrincipal dp) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                        new UsernamePasswordAuthenticationToken(dp, null, List.of()));
                SecurityContextHolder.setContext(context);
            }
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
