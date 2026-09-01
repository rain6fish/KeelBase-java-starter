// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.pmsample;

import cn.com.keelbase.annotation.KeelbaseTool;
import cn.com.keelbase.compensation.CompensationAuditSink;
import cn.com.keelbase.compensation.KeelBaseCompensationSupport;
import cn.com.keelbase.compensation.RevocationLedgerStore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 存量 Java PM 的受治理 AI 工具：
 * 读项目/任务（R1 自动）+ 创建项目任务（写 R3 需确认，revokePath → 补偿端点）。
 *
 * <p>对齐 KeelBase AI Project 旗舰（项目延期风险分析）的业务数据面——AI 读项目/任务
 * 判断延期风险，写任务创建需人工确认，撤销走补偿端点（幂等 + 审计 + 委托身份）。
 */
@RestController
@RequestMapping("/api")
public class PmController extends KeelBaseCompensationSupport<PmTask> {

    private final PmStore store;

    public PmController(RevocationLedgerStore ledger, CompensationAuditSink auditSink, PmStore store) {
        super(ledger, auditSink);
        this.store = store;
    }

    @GetMapping("/projects")
    @KeelbaseTool(name = "query_projects", description = "项目列表（读工具，R1 自动）——项目状态/风险等级/描述，AI 延期风险分析的依据")
    public List<PmProject> listProjects() {
        return store.listProjects();
    }

    @GetMapping("/projects/{id}")
    @KeelbaseTool(name = "get_project", description = "项目详情（读工具，R1 自动）——含里程碑/任务清单")
    public ResponseEntity<?> getProject(@PathVariable long id) {
        PmProject project = store.project(id);
        return project == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(project);
    }

    @PostMapping("/projects/{id}/tasks")
    @KeelbaseTool(name = "create_pm_task",
            description = "为项目创建任务（写工具，R3 需人工确认；撤销走补偿端点）",
            revokePath = "DELETE /api/compensation/pm-tasks/{id}")
    public ResponseEntity<?> createTask(@PathVariable long id, @RequestBody CreateTaskRequest req) {
        if (store.project(id) == null) {
            return ResponseEntity.notFound().build();
        }
        PmTask task = store.addTask(id, req.title());
        return task == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(task);
    }

    /** 补偿端点：KeelBase 撤销 AI 创建的任务时调用（幂等 + 审计 + 委托身份）。 */
    @DeleteMapping("/compensation/pm-tasks/{id}")
    public ResponseEntity<?> revoke(@PathVariable long id, HttpServletRequest request) {
        return handleRevoke(request, id, store::task,
                (task, subject) -> store.replaceTask(task.cancel()),
                "compensation.pm-task.revoke");
    }

    /** 任务创建请求（写工具 body，字段对齐 KeelBase 生成器 DTO）。 */
    public record CreateTaskRequest(
            @Schema(description = "任务标题", required = true) String title) {
    }
}
