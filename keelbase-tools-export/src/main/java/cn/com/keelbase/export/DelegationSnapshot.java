// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.export;

import java.util.List;

/**
 * 委托配置的诊断快照（供 {@code /keelbase/status} 消费）。
 *
 * <p>由自动配置模块从 {@code DelegationProperties} 组装——只带状态与标识，
 * <b>绝不含密钥明文</b>，避免诊断端点泄露 {@code keelbase.delegation.secret}。
 */
public record DelegationSnapshot(
        boolean configured,
        boolean secretConfigured,
        String audience,
        String issuer,
        List<String> protectedPaths) {

    /** 未装配委托能力（keelbase.delegation.enabled=false 或缺 DelegationProperties bean）时使用。 */
    public static DelegationSnapshot unconfigured() {
        return new DelegationSnapshot(false, false, null, null, List.of());
    }
}
