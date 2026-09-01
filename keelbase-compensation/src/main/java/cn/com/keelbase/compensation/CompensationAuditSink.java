// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.compensation;

/**
 * 补偿审计钩子：撤销动作的可追溯记录（谁 / 何时 / 补偿什么）。
 *
 * <p>默认实现 {@link Slf4jCompensationAuditSink}（SLF4J 日志）；需要把补偿动作写入业务审计
 * 表/与 KeelBase 审计哈希链呼应时，实现本接口并注册为 bean 覆盖默认。
 */
@FunctionalInterface
public interface CompensationAuditSink {

    /**
     * @param action    审计动作标识（如 "compensation.followups.revoke"）
     * @param resultId  被撤销的副作用 resultId
     * @param subject   发起撤销的委托身份（oidcSub / local 用户标识）
     */
    void audit(String action, Long resultId, String subject);
}
