package cn.com.keelbase.pmsample;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 内存存储 + 种子数据（模拟存量 Java PM 的项目库）。 */
@Component
public class PmStore {

    private final Map<Long, PmProject> projects = new LinkedHashMap<>();
    private final AtomicLong taskSeq = new AtomicLong(100);

    public PmStore() {
        seed();
    }

    private void seed() {
        projects.put(1L, new PmProject(1L, "电商平台重构",
                "active", "high",
                "Q3 核心项目：订单与库存模块重构，涉及 6 个服务",
                List.of(new PmTask(1, 1L, "需求冻结", "completed", false),
                        new PmTask(2, 1L, "订单模块上线", "in_progress", false),
                        new PmTask(3, 1L, "库存模块上线", "todo", false))));
        projects.put(2L, new PmProject(2L, "移动端 App 发布",
                "active", "medium",
                "双端打包与商店上架",
                List.of(new PmTask(4, 2L, "iOS 提审", "in_progress", false),
                        new PmTask(5, 2L, "Android 灰度", "todo", false))));
        projects.put(3L, new PmProject(3L, "数据仓库迁移",
                "on_hold", "medium",
                "遗留 ETL 迁到新数仓",
                List.of(new PmTask(6, 3L, "数据核对脚本", "todo", false))));
    }

    public List<PmProject> listProjects() {
        List<PmProject> out = new ArrayList<>();
        for (PmProject p : projects.values()) {
            out.add(p.withoutTasks());
        }
        return out;
    }

    public PmProject project(long id) {
        return projects.get(id);
    }

    public PmTask task(long id) {
        for (PmProject p : projects.values()) {
            for (PmTask t : p.tasks()) {
                if (t.id() == id) {
                    return t;
                }
            }
        }
        return null;
    }

    public PmTask addTask(long projectId, String title) {
        PmProject p = projects.get(projectId);
        if (p == null) {
            return null;
        }
        PmTask task = new PmTask(taskSeq.incrementAndGet(), projectId, title, "todo", false);
        List<PmTask> tasks = new ArrayList<>(p.tasks());
        tasks.add(task);
        projects.put(projectId, new PmProject(p.id(), p.name(), p.status(), p.riskLevel(), p.description(), tasks));
        return task;
    }

    public PmTask replaceTask(PmTask task) {
        for (PmProject p : projects.values()) {
            if (p.id() == task.projectId()) {
                List<PmTask> tasks = new ArrayList<>(p.tasks());
                for (int i = 0; i < tasks.size(); i++) {
                    if (tasks.get(i).id() == task.id()) {
                        tasks.set(i, task);
                    }
                }
                projects.put(p.id(), new PmProject(p.id(), p.name(), p.status(), p.riskLevel(), p.description(), tasks));
            }
        }
        return task;
    }
}
