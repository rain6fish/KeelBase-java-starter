// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.annotation;

/**
 * AI 工具风险级，对齐 KeelBase 治理层（HS-9 / tool.interface.ts R0-R5）。
 *
 * <p>KeelBase 工具执行时按风险级决定治理策略：R0-R2 自动（低风险），R3 需人工确认，
 * R4 双人审批，R5 阻断。Java Starter 导出工具时把风险级写入 {@code ai_proxy_tools}
 * 配置，KeelBase 侧据此自动套用对应治理。
 */
public enum KeelbaseRiskLevel {
    /** 自动推断：GET → R1（读自动），POST/PUT/PATCH/DELETE → R3（写需确认）。 */
    AUTO,
    R0,
    R1,
    R2,
    R3,
    R4,
    R5
}
