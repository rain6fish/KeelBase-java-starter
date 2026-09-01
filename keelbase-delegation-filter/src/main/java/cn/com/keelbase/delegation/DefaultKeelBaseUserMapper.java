// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.delegation;

import java.util.Optional;

/** 默认映射：返回 oidcSub / local 前缀剥离后的用户标识字符串。 */
public class DefaultKeelBaseUserMapper implements KeelBaseUserMapper {

    @Override
    public Optional<?> map(DelegationPrincipal principal) {
        return Optional.ofNullable(principal.identity());
    }
}
