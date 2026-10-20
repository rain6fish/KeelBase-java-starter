// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.compensation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Compensation scaffold configuration ({@code keelbase.compensation}).
 *
 * <p>补偿脚手架配置（前缀 {@code keelbase.compensation}）。
 */
@ConfigurationProperties(prefix = "keelbase.compensation")
public class CompensationProperties {

    /** Ledger backend. / 账本后端。 */
    public enum Store {
        /** Process-local LRU, correct for a single instance. / 进程内 LRU，单实例正确。 */
        MEMORY,
        /** State shared through a DataSource, for multi-instance hosts. / 经 DataSource 共享状态，供多实例宿主使用。 */
        JDBC
    }

    /** 幂等账本容量（LRU 上限），缺省 1024。 */
    private int ledgerSize = 1024;

    /** 账本后端，缺省 {@code memory}；{@code jdbc} 需要宿主提供 DataSource。 */
    private Store store = Store.MEMORY;

    /** {@code store=jdbc} 时的建表选项。 */
    private Jdbc jdbc = new Jdbc();

    public int getLedgerSize() { return ledgerSize; }
    public void setLedgerSize(int ledgerSize) { this.ledgerSize = ledgerSize; }
    public Store getStore() { return store; }
    public void setStore(Store store) { this.store = store; }
    public Jdbc getJdbc() { return jdbc; }
    public void setJdbc(Jdbc jdbc) { this.jdbc = jdbc; }

    /** JDBC ledger options ({@code keelbase.compensation.jdbc.*}). / JDBC 账本选项。 */
    public static class Jdbc {

        /** Create the ledger table at startup when missing (default true). / 启动时缺表则建表（缺省 true）。 */
        private boolean autoDdl = true;

        public boolean isAutoDdl() { return autoDdl; }
        public void setAutoDdl(boolean autoDdl) { this.autoDdl = autoDdl; }
    }
}
