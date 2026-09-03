package cn.com.keelbase.client;

// SPDX-License-Identifier: Apache-2.0

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 治理台实时治理策略（D2-3：业务系统拉取）。
 *
 * <p><b>合并语义</b>：{@code tools} 只含被覆盖的字段（partial override）——未配置的工具/维度不出现。
 * 消费方须与自身工具定义默认值合并才能得到「生效值」；无策略行时返回空 tools + {@code auditGranularity="all"}。
 */
public record GovernancePolicy(
        Map<String, ToolOverride> tools,
        String auditGranularity,
        String updatedAt) {

    /** 单个工具的治理覆盖：缺省 {@code null} 表示不覆盖（沿用本地默认）。 */
    public record ToolOverride(Boolean enabled, Boolean requiresConfirmation, List<String> allowedRoles) {
        public static ToolOverride from(JsonNode node) {
            if (node == null || !node.isObject()) {
                return null;
            }
            Boolean enabled = node.hasNonNull("enabled") ? node.path("enabled").asBoolean() : null;
            Boolean confirmation = node.hasNonNull("requiresConfirmation")
                    ? node.path("requiresConfirmation").asBoolean() : null;
            List<String> roles = new ArrayList<>();
            if (node.hasNonNull("allowedRoles") && node.path("allowedRoles").isArray()) {
                node.path("allowedRoles").forEach(r -> roles.add(r.asText()));
            }
            return new ToolOverride(enabled, confirmation, roles.isEmpty() ? null : roles);
        }
    }

    public static GovernancePolicy from(JsonNode data) {
        if (data == null || !data.isObject()) {
            return empty();
        }
        Map<String, ToolOverride> tools = new LinkedHashMap<>();
        if (data.hasNonNull("tools") && data.path("tools").isObject()) {
            data.path("tools").fields().forEachRemaining(e -> {
                ToolOverride override = ToolOverride.from(e.getValue());
                if (override != null) {
                    tools.put(e.getKey(), override);
                }
            });
        }
        String granularity = data.path("audit").path("granularity").asText("all");
        String updatedAt = data.hasNonNull("updatedAt") ? data.path("updatedAt").asText() : null;
        return new GovernancePolicy(tools, granularity, updatedAt);
    }

    /** 无策略行：空 tools + 默认审计粒度 all。 */
    public static GovernancePolicy empty() {
        return new GovernancePolicy(Map.of(), "all", null);
    }
}
