// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.autoconfigure;

import cn.com.keelbase.compensation.CompensationAuditSink;
import cn.com.keelbase.compensation.CompensationProperties;
import cn.com.keelbase.compensation.JdbcRevocationLedgerStore;
import cn.com.keelbase.compensation.KeelBaseCompensationSupport;
import cn.com.keelbase.compensation.RevocationLedger;
import cn.com.keelbase.compensation.RevocationLedgerStore;
import cn.com.keelbase.compensation.Slf4jCompensationAuditSink;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Wires the compensation scaffold: an idempotency ledger and an audit sink, both overridable by a
 * user bean. The ledger backend is selected by {@code keelbase.compensation.store}: {@code memory}
 * (default, single instance) or {@code jdbc} (shared through the host's DataSource).
 *
 * <p>自动装配补偿脚手架：默认幂等账本（内存 LRU）+ 默认审计实现（SLF4J），均可被用户 bean 覆盖。
 * 账本后端由 {@code keelbase.compensation.store} 选择：{@code memory}（缺省，单实例）或
 * {@code jdbc}（经宿主的 DataSource 共享）。
 *
 * <p>{@code store=jdbc} without a DataSource, a missing ledger table, or an unknown store value
 * fails startup with the offending key named — no silent fallback.
 *
 * <p>{@code store=jdbc} 但无 DataSource、账本表缺失、或后端取值未知时，启动失败并点名对应配置键，
 * 不静默回退。
 */
@AutoConfiguration
@EnableConfigurationProperties(CompensationProperties.class)
@ConditionalOnClass(KeelBaseCompensationSupport.class)
public class CompensationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RevocationLedgerStore.class)
    RevocationLedgerStore keelBaseRevocationLedger(CompensationProperties properties,
                                                   ObjectProvider<DataSource> dataSource) {
        if (properties.getStore() == CompensationProperties.Store.JDBC) {
            DataSource ds = dataSource.getIfAvailable();
            if (ds == null) {
                throw new IllegalStateException(
                        "keelbase.compensation.store=jdbc 但未找到 DataSource bean：请引入提供 DataSource 的"
                                + " starter（spring-boot-starter-jdbc / jpa / mybatis 等），"
                                + "或把 keelbase.compensation.store 改回 memory");
            }
            JdbcRevocationLedgerStore ledger = new JdbcRevocationLedgerStore(ds);
            if (properties.getJdbc().isAutoDdl()) {
                ledger.ensureSchema();
            }
            ledger.validate();
            return ledger;
        }
        return new RevocationLedger(properties.getLedgerSize());
    }

    @Bean
    @ConditionalOnMissingBean(CompensationAuditSink.class)
    CompensationAuditSink keelBaseCompensationAuditSink() {
        return new Slf4jCompensationAuditSink();
    }
}
