package cn.com.keelbase.pmsample;

/** 项目任务实体（写工具创建 + 可撤销的目标）。 */
public record PmTask(
        long id,
        long projectId,
        String title,
        String status, // todo | in_progress | done
        boolean cancelled) {

    public PmTask markDone() {
        return new PmTask(id, projectId, title, "done", cancelled);
    }

    public PmTask cancel() {
        return new PmTask(id, projectId, title, "cancelled", true);
    }
}
