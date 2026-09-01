// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.client;

/**
 * 委托 token 签发结果（对齐 KeelBase {@code POST /api/v1/auth/delegation-token} 响应 data）。
 *
 * @param token     短期委托 JWT
 * @param subject   oidcSub 或 {@code local:<userId>}（Java 端映射本地用户的键）
 * @param expiresIn 有效期（秒）
 * @param userId    KeelBase userId
 * @param audience  目标系统标识
 */
public record KeelbaseTokenIssue(
        String token,
        String subject,
        int expiresIn,
        String userId,
        String audience) {
}
