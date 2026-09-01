// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.client;

/**
 * Java 侧审计事件（对齐治理台 {@code POST /api/v1/external/audit} 契约，D2-3a）。
 *
 * <p>通过 {@link KeelbaseAuditReporter#report} 异步上报，落治理库审计哈希链；
 * {@code source} 缺省 {@code "java"}（业务系统来源，区别于 KeelBase 内部 {@code chat} 等）。
 */
public record KeelbaseAuditEvent(
        String userId,
        String username,
        String action,
        String detail,
        String model,
        String provider,
        String agentId,
        String source,
        Integer promptTokens,
        Integer completionTokens,
        Long durationMs,
        boolean isError,
        String errorMessage) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String userId;
        private String username;
        private String action = "chat";
        private String detail;
        private String model;
        private String provider;
        private String agentId;
        private String source;
        private Integer promptTokens;
        private Integer completionTokens;
        private Long durationMs;
        private boolean isError;
        private String errorMessage;

        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder detail(String detail) { this.detail = detail; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder provider(String provider) { this.provider = provider; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder promptTokens(Integer promptTokens) { this.promptTokens = promptTokens; return this; }
        public Builder completionTokens(Integer completionTokens) { this.completionTokens = completionTokens; return this; }
        public Builder durationMs(Long durationMs) { this.durationMs = durationMs; return this; }
        public Builder isError(boolean isError) { this.isError = isError; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public KeelbaseAuditEvent build() {
            return new KeelbaseAuditEvent(userId, username, action, detail, model, provider, agentId,
                    source == null ? "java" : source,
                    promptTokens, completionTokens, durationMs, isError, errorMessage);
        }
    }
}
