// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.compensation;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * DataSource-backed idempotency ledger for hosts that need revocation state shared across
 * instances.
 *
 * <p>It uses plain JDBC through a {@link DataSource}, not an ORM API: JPA/Hibernate, MyBatis and
 * plain JdbcTemplate hosts all provide a DataSource, so none of them is required or excluded.
 * The table is owned by this library and has no configurable name (avoiding identifier injection).
 *
 * <p>基于 DataSource 的幂等账本，供多实例宿主共享撤销状态。只通过 {@link DataSource} 使用普通
 * JDBC，不依赖具体 ORM：JPA/Hibernate、MyBatis、纯 JdbcTemplate 宿主都提供 DataSource，因此都可用。
 * 表由本库拥有且表名不可配置（避免标识符注入）。
 *
 * <p>Semantics match {@link RevocationLedger}: {@link #markRevoked(long)} returns {@code true} only
 * on the first successful mark; a duplicate row means another instance already marked it.
 * 语义与 {@link RevocationLedger} 一致：只有首次成功写入才返回 {@code true}。
 */
public class JdbcRevocationLedgerStore implements RevocationLedgerStore {

    /** The ledger table owned by this library. / 本库拥有的账本表名。 */
    public static final String TABLE = "keelbase_revocation_ledger";

    /** DDL that creates the ledger table; safe to run repeatedly on H2/PostgreSQL/MySQL/MariaDB. */
    public static final String CREATE_TABLE_DDL = "CREATE TABLE IF NOT EXISTS " + TABLE
            + " (result_id BIGINT PRIMARY KEY, revoked_at TIMESTAMP NOT NULL)";

    private final DataSource dataSource;

    public JdbcRevocationLedgerStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Creates the ledger table when it does not exist. Call it only when the application is allowed
     * to run DDL at startup; otherwise create the table from {@link #CREATE_TABLE_DDL} yourself.
     *
     * <p>建表（已存在则跳过）。仅在允许启动时跑 DDL 的场景调用；否则用 {@link #CREATE_TABLE_DDL} 自行建表。
     */
    public void ensureSchema() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE_DDL);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "keelbase.compensation.store=jdbc：创建账本表 " + TABLE
                            + " 失败。请让该数据库账号具备建表权限，或关闭 auto-ddl 并手工执行："
                            + CREATE_TABLE_DDL, e);
        }
    }

    /**
     * Verifies that the ledger table is reachable, so a missing table fails at startup instead of
     * on the first revoke.
     *
     * <p>启动时探测账本表是否可用——缺表在启动即失败，而不是等到第一次撤销才报错。
     */
    public void validate() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet ignored = statement.executeQuery(
                    "SELECT result_id FROM " + TABLE + " WHERE 1 = 0")) {
                // Probe only: reaching this point proves the table exists and is queryable.
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "keelbase.compensation.store=jdbc：账本表 " + TABLE
                            + " 不可用。请关闭 auto-ddl 后手工执行："
                            + CREATE_TABLE_DDL, e);
        }
    }

    @Override
    public boolean markRevoked(long resultId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO " + TABLE + " (result_id, revoked_at) VALUES (?, CURRENT_TIMESTAMP)")) {
            statement.setLong(1, resultId);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            // SQLState class 23 = integrity constraint violation: the row already exists, so this
            // id was marked by this or another instance.
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                return false;
            }
            throw new IllegalStateException(
                    "keelbase.compensation.store=jdbc：写入账本失败（resultId=" + resultId + "）", e);
        }
    }

    @Override
    public boolean isRevoked(long resultId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM " + TABLE + " WHERE result_id = ?")) {
            statement.setLong(1, resultId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "keelbase.compensation.store=jdbc：查询账本失败（resultId=" + resultId + "）", e);
        }
    }
}
