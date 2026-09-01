// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.pmsample;

import java.util.List;

/** 项目实体（存量 Java PM 的项目记录）。 */
public record PmProject(
        long id,
        String name,
        String status,     // planning | active | on_hold | completed
        String riskLevel,  // low | medium | high
        String description,
        List<PmTask> tasks) {

    /** 无任务的精简视图（列表用）。 */
    public PmProject withoutTasks() {
        return new PmProject(id, name, status, riskLevel, description, List.of());
    }
}
