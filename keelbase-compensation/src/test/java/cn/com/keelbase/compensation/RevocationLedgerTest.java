package cn.com.keelbase.compensation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3 验收：幂等账本。 */
class RevocationLedgerTest {

    @Test
    void markRevoked_firstTrue_thenFalse_andIsRevoked() {
        RevocationLedger ledger = new RevocationLedger(16);
        assertTrue(ledger.markRevoked(42L));
        assertFalse(ledger.markRevoked(42L), "重复标记应返回 false");
        assertTrue(ledger.isRevoked(42L));
        assertFalse(ledger.isRevoked(99L));
    }
}
