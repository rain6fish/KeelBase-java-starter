// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.compensation;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the DataSource-backed ledger against a real database: first mark wins, duplicates are
 * idempotent, state is shared across instances, and a missing table fails with the DDL named.
 *
 * <p>用真库验证 DataSource 账本：首次标记成功、重复标记幂等、跨实例共享状态、缺表失败并给出 DDL。
 */
class JdbcRevocationLedgerStoreTest {

    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ledger_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    @Test
    void ensureSchemaIsIdempotentAndFirstMarkWins() {
        JdbcRevocationLedgerStore store = new JdbcRevocationLedgerStore(dataSource);
        store.ensureSchema();
        store.ensureSchema(); // second call must be a no-op

        assertThat(store.markRevoked(42L)).isTrue();
        assertThat(store.markRevoked(42L)).isFalse();
        assertThat(store.isRevoked(42L)).isTrue();
        assertThat(store.isRevoked(43L)).isFalse();
    }

    @Test
    void stateIsSharedAcrossInstances() {
        JdbcRevocationLedgerStore first = new JdbcRevocationLedgerStore(dataSource);
        first.ensureSchema();
        first.markRevoked(7L);

        JdbcRevocationLedgerStore second = new JdbcRevocationLedgerStore(dataSource);
        assertThat(second.isRevoked(7L)).isTrue();
        assertThat(second.markRevoked(7L)).isFalse();
    }

    @Test
    void validateFailsFastWhenTableIsMissing() {
        JdbcRevocationLedgerStore store = new JdbcRevocationLedgerStore(dataSource);

        assertThatThrownBy(store::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(JdbcRevocationLedgerStore.TABLE)
                .hasMessageContaining("CREATE TABLE IF NOT EXISTS");
    }

    @Test
    void validatePassesAfterSchemaIsCreated() {
        JdbcRevocationLedgerStore store = new JdbcRevocationLedgerStore(dataSource);
        store.ensureSchema();

        store.validate();
    }
}
