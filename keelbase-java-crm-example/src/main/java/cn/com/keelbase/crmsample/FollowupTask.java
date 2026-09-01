// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.crmsample;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

/** 跟进任务（可变：撤销补偿时置 cancelled）。record 风格 getter，字段直接序列化。 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class FollowupTask {

    private final long id;
    private final long customerId;
    private final String content;
    private final String dueDate;
    private final String createdBy;
    private boolean cancelled;
    private String cancelledBy;

    public FollowupTask(long id, long customerId, String content, String dueDate, String createdBy) {
        this.id = id;
        this.customerId = customerId;
        this.content = content;
        this.dueDate = dueDate;
        this.createdBy = createdBy;
    }

    public long id() { return id; }
    public long customerId() { return customerId; }
    public String content() { return content; }
    public String dueDate() { return dueDate; }
    public String createdBy() { return createdBy; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public String cancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
}
