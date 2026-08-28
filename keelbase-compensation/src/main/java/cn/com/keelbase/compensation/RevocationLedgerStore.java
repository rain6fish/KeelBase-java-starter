package cn.com.keelbase.compensation;

/**
 * 撤销幂等账本 SPI：记录已撤销的 resultId，保证重复撤销返回幂等语义。
 *
 * <p>默认实现 {@link RevocationLedger}（进程内 LRU）；多实例/持久化场景可实现本接口
 * 接 DB（JdbcTemplate 等），并注册为 bean 覆盖默认。
 */
public interface RevocationLedgerStore {

    /** 标记 resultId 已撤销。返回是否首次标记（true=首次，false=已标记过）。 */
    boolean markRevoked(long resultId);

    /** 该 resultId 是否已撤销。 */
    boolean isRevoked(long resultId);
}
