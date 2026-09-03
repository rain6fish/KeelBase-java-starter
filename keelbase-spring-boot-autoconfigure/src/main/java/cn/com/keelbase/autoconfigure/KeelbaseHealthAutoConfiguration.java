package cn.com.keelbase.autoconfigure;

// SPDX-License-Identifier: Apache-2.0

import cn.com.keelbase.export.KeelbaseStatusController;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 可选 actuator 集成：把 {@code /keelbase/status} 接入 {@code /actuator/health}。
 *
 * <p>{@code @ConditionalOnClass(HealthIndicator)}——消费者需自加 {@code spring-boot-starter-actuator}
 * 依赖才装配（starter 不强制带 actuator）；{@code keelbase.health.enabled=false} 可关。
 */
@AutoConfiguration
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(KeelbaseStatusController.class)
@ConditionalOnProperty(prefix = "keelbase.health", name = "enabled", matchIfMissing = true)
public class KeelbaseHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    KeelbaseHealthIndicator keelbaseHealthIndicator(KeelbaseStatusController statusController) {
        return new KeelbaseHealthIndicator(statusController);
    }
}
