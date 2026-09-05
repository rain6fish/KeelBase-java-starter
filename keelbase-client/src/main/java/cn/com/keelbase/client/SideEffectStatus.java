package cn.com.keelbase.client;

// SPDX-License-Identifier: Apache-2.0

/**
 * AI 副作用状态（服务身份查询结果，对齐主仓 {@code GET /api/v1/external/effects/:resultType/:resultId}）。
 *
 * <p>本地实体（event/todo/crm_task 等）{@code revoked = targetSoftDeleted} 是撤销真值；
 * B 路径 {@code proxy_call} 主库 effect 无撤销列、撤销经 Java 补偿端点 → {@code revokeHint}
 * 明示「撤销态需在 Java 侧确认」（诚实边界，不夸大）。
 *
 * @param effectId        副作用记录 id（{@code found=false} 时为 0）
 * @param toolName        触发工具名（如 create_followup_task / proxy_create_followup）
 * @param resultType      业务动作类型（如 crm_task / proxy_call）
 * @param resultId        业务动作 id
 * @param revoked         是否已撤销（本地实体 = 目标软删；proxy_call 需见 revokeHint）
 * @param targetExists    目标记录是否存在（主应用本地实体判定）
 * @param targetSoftDeleted 目标是否软删
 * @param targetTitle     目标标题（可空）
 * @param revokeHint      撤销语义提示（proxy_call 非空）
 * @param found           false = 无该副作用记录（404），Java 侧按「非 AI 创建/已不存在」处理
 */
public record SideEffectStatus(
        long effectId,
        String toolName,
        String resultType,
        long resultId,
        boolean revoked,
        Boolean targetExists,
        Boolean targetSoftDeleted,
        String targetTitle,
        String revokeHint,
        boolean found) {

    /** 无该副作用记录（HTTP 404）——非 AI 创建或已不存在，Java 侧可正常处理。 */
    public static SideEffectStatus notFound(String resultType, long resultId) {
        return new SideEffectStatus(0, null, resultType, resultId, false, null, null, null, null, false);
    }
}
