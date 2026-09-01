// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.autoconfigure;

import cn.com.keelbase.delegation.DefaultKeelBaseUserMapper;
import cn.com.keelbase.delegation.DelegationAuthFilter;
import cn.com.keelbase.delegation.DelegationProperties;
import cn.com.keelbase.delegation.DelegationUserArgumentResolver;
import cn.com.keelbase.delegation.KeelBaseUserMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 自动装配委托验签能力：
 * <ul>
 *   <li>{@link DelegationProperties}（{@code keelbase.delegation.*} 配置）</li>
 *   <li>默认 {@link KeelBaseUserMapper}（可被用户 bean 覆盖）</li>
 *   <li>{@link DelegationAuthFilter}（经 {@link FilterRegistrationBean} 注册为 servlet 过滤器，
 *       order 在 Spring Security 之前）</li>
 *   <li>{@link DelegationUserArgumentResolver}（支持 {@code @DelegationUser DelegationPrincipal} 参数）</li>
 *   <li>可选：classpath 含 Spring Security 时注册 {@link SecurityDelegationWriter}（写入 SecurityContext）</li>
 * </ul>
 *
 * <p>开关：{@code keelbase.delegation.enabled=false} 可整体关闭。未配置 {@code keelbase.delegation.secret}
 * 时启动抛 {@link IllegalStateException}（委托验签是安全组件，缺密钥即失败，不静默降级）。
 */
@AutoConfiguration
@EnableConfigurationProperties(DelegationProperties.class)
@ConditionalOnClass(DelegationAuthFilter.class)
@ConditionalOnProperty(prefix = "keelbase.delegation", name = "enabled", matchIfMissing = true)
public class DelegationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(KeelBaseUserMapper.class)
    KeelBaseUserMapper keelBaseUserMapper() {
        return new DefaultKeelBaseUserMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    DelegationAuthFilter keelBaseDelegationAuthFilter(DelegationProperties properties,
                                                      KeelBaseUserMapper userMapper) {
        return new DelegationAuthFilter(properties, userMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    DelegationUserArgumentResolver keelBaseDelegationUserArgumentResolver() {
        return new DelegationUserArgumentResolver();
    }

    @Bean
    WebMvcConfigurer keelBaseWebMvcConfigurer(DelegationUserArgumentResolver resolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(resolver);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "keelBaseDelegationFilterRegistration")
    FilterRegistrationBean<DelegationAuthFilter> keelBaseDelegationFilterRegistration(
            DelegationAuthFilter filter) {
        FilterRegistrationBean<DelegationAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    @ConditionalOnMissingBean
    SecurityDelegationWriter keelBaseSecurityDelegationWriter() {
        return new SecurityDelegationWriter();
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    @ConditionalOnMissingBean(name = "keelBaseSecurityDelegationWriterRegistration")
    FilterRegistrationBean<SecurityDelegationWriter> keelBaseSecurityDelegationWriterRegistration(
            SecurityDelegationWriter writer) {
        FilterRegistrationBean<SecurityDelegationWriter> reg = new FilterRegistrationBean<>(writer);
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 11);
        return reg;
    }
}
