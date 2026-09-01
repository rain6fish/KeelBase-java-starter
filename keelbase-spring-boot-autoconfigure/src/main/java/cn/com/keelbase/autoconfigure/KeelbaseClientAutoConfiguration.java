// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.autoconfigure;

import cn.com.keelbase.client.KeelbaseAuditProperties;
import cn.com.keelbase.client.KeelbaseAuditReporter;
import cn.com.keelbase.client.KeelbaseClient;
import cn.com.keelbase.client.KeelbaseClientProperties;
import cn.com.keelbase.delegation.DelegationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 自动装配 KeelbaseClient + 审计上报：
 * <ul>
 *   <li>{@link KeelbaseClientProperties}（{@code keelbase.client.*}）</li>
 *   <li>{@link KeelbaseAuditProperties}（{@code keelbase.audit.*}）</li>
 *   <li>{@link KeelbaseClient}——委托 token 获取/缓存/验签；delegation secret/audience 从
 *       {@link DelegationProperties} 回退（经 ObjectProvider，委托能力关闭时也不阻塞装配）</li>
 *   <li>{@link KeelbaseAuditReporter}——审计事件异步上报治理台</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties({KeelbaseClientProperties.class, KeelbaseAuditProperties.class})
@ConditionalOnClass(KeelbaseClient.class)
public class KeelbaseClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    KeelbaseClient keelbaseClient(KeelbaseClientProperties properties,
                                  ObjectProvider<DelegationProperties> delegation) {
        DelegationProperties d = delegation.getIfAvailable();
        return new KeelbaseClient(properties,
                d == null ? null : d.getSecret(),
                d == null ? null : d.getAudience());
    }

    @Bean
    @ConditionalOnMissingBean
    KeelbaseAuditReporter keelbaseAuditReporter(KeelbaseAuditProperties properties) {
        return new KeelbaseAuditReporter(properties);
    }
}
