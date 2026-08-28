package cn.com.keelbase.compensation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 补偿脚手架配置（前缀 {@code keelbase.compensation}）。 */
@ConfigurationProperties(prefix = "keelbase.compensation")
public class CompensationProperties {

    /** 幂等账本容量（LRU 上限），缺省 1024。 */
    private int ledgerSize = 1024;

    public int getLedgerSize() { return ledgerSize; }
    public void setLedgerSize(int ledgerSize) { this.ledgerSize = ledgerSize; }
}
