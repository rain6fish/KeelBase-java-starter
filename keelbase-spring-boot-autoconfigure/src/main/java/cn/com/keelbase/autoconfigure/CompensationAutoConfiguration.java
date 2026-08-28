package cn.com.keelbase.autoconfigure;

import cn.com.keelbase.compensation.CompensationAuditSink;
import cn.com.keelbase.compensation.CompensationProperties;
import cn.com.keelbase.compensation.KeelBaseCompensationSupport;
import cn.com.keelbase.compensation.RevocationLedger;
import cn.com.keelbase.compensation.RevocationLedgerStore;
import cn.com.keelbase.compensation.Slf4jCompensationAuditSink;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 自动装配补偿脚手架：默认幂等账本（内存 LRU）+ 默认审计实现（SLF4J），均可被用户 bean 覆盖。
 */
@AutoConfiguration
@EnableConfigurationProperties(CompensationProperties.class)
@ConditionalOnClass(KeelBaseCompensationSupport.class)
public class CompensationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RevocationLedgerStore.class)
    RevocationLedgerStore keelBaseRevocationLedger(CompensationProperties properties) {
        return new RevocationLedger(properties.getLedgerSize());
    }

    @Bean
    @ConditionalOnMissingBean(CompensationAuditSink.class)
    CompensationAuditSink keelBaseCompensationAuditSink() {
        return new Slf4jCompensationAuditSink();
    }
}
