package cn.com.keelbase.compensation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 进程内幂等账本（LRU 上限，默认 1024，可配 {@code keelbase.compensation.ledger-size}）。
 *
 * <p>MVP 内存版：单实例足够。多实例/高可用需要实现 {@link RevocationLedgerStore} 接持久化存储。
 */
public class RevocationLedger implements RevocationLedgerStore {

    private final int capacity;
    private final Map<Long, Boolean> cache;

    public RevocationLedger(int capacity) {
        this.capacity = Math.max(capacity, 16);
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                return size() > RevocationLedger.this.capacity;
            }
        };
    }

    @Override
    public synchronized boolean markRevoked(long resultId) {
        Boolean prev = cache.put(resultId, Boolean.TRUE);
        return !Boolean.TRUE.equals(prev);
    }

    @Override
    public synchronized boolean isRevoked(long resultId) {
        return Boolean.TRUE.equals(cache.get(resultId));
    }
}
