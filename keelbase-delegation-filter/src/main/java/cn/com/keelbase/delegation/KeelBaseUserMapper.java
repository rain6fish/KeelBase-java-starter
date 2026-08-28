package cn.com.keelbase.delegation;

import java.util.Optional;

/**
 * 把委托身份映射为本地用户对象的 SPI。
 *
 * <p>默认实现（{@link DefaultKeelBaseUserMapper}）返回 {@link DelegationPrincipal#identity()}
 * 字符串；需要把 oidcSub / local 用户映射为你的 User 实体时，实现本接口并注册为 bean
 * 覆盖默认。
 */
@FunctionalInterface
public interface KeelBaseUserMapper {

    /**
     * 映射委托身份 → 本地用户对象。
     *
     * @param principal 验签通过后的委托身份
     * @return 本地用户对象；空表示无映射（调用方按需处理）
     */
    Optional<?> map(DelegationPrincipal principal);
}
