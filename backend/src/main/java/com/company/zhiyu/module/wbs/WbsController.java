package com.company.zhiyu.module.wbs;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.audit.AuditLog;
import com.company.zhiyu.common.security.RequireRoles;
import com.company.zhiyu.module.wbs.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * WBS / EVM / 资源分配 HTTP 入口。
 * <p>URL 设计: 一律 /api/wbs 前缀, 子资源平铺, 避免太深。
 */
@RestController
@RequestMapping("/wbs")
@RequiredArgsConstructor
@Tag(name = "WBS", description = "工作分解结构 / 资源分配 / EVM 快照")
public class WbsController {

    private final WbsService wbsService;

    // ---- WBS 任务 ----

    @GetMapping("/tasks/by-project/{projectId}")
    @RequireRoles.Read
    @Operation(summary = "某项目的 WBS 任务树 (扁平+嵌套 children, 给 el-tree 用)")
    public ApiResponse<List<WbsTaskNode>> tree(@PathVariable Long projectId) {
        return ApiResponse.ok(wbsService.listTreeByProject(projectId));
    }

    @GetMapping("/tasks/flat/by-project/{projectId}")
    @RequireRoles.Read
    @Operation(summary = "某项目的 WBS 任务扁平列表 (给搜索/下拉用)")
    public ApiResponse<List<WbsTaskNode>> flat(@PathVariable Long projectId) {
        return ApiResponse.ok(wbsService.listFlatByProject(projectId));
    }

    @GetMapping("/tasks/{id}")
    @RequireRoles.Read
    @Operation(summary = "单个任务详情")
    public ApiResponse<WbsTaskNode> get(@PathVariable Long id) {
        WbsTask t = wbsService.getById(id);
        return ApiResponse.ok(WbsTaskNode.leaf(t, 0, List.of(t.getWbsCode())));
    }

    @PostMapping("/tasks")
    @RequireRoles.Operate
    @AuditLog(module = "WBS", action = "CREATE")
    @Operation(summary = "新建/更新任务 (id=null 新建, id!=null 更新)")
    public ApiResponse<WbsTaskNode> save(@Valid @RequestBody WbsTaskRequest req) {
        WbsTask t = wbsService.save(req);
        return ApiResponse.ok(WbsTaskNode.leaf(t, 0, List.of(t.getWbsCode())));
    }

    @DeleteMapping("/tasks/{id}")
    @RequireRoles.Operate
    @AuditLog(module = "WBS", action = "DELETE", extractResourceId = false)
    @Operation(summary = "软删除任务 (子任务不会级联, 需手动删)")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        wbsService.softDelete(id);
        return ApiResponse.ok(null);
    }

    /**
     * P4-WBS 拖拽重排 (a + b 步) — 移动任务到新父下, 或同 parent 内重排。
     * <p>body 字段 (两者都给时, <b>beforeSiblingId 优先</b>, 走 b 步):
     * <ul>
     *   <li>{@code newParentId} — 目标父 id (null = 顶层)</li>
     *   <li>{@code beforeSiblingId} — 拖到该 sibling 之前 (null = 末尾 / 走 a 步)</li>
     * </ul>
     * <p>dispatch 规则:
     * <ul>
     *   <li>{@code beforeSiblingId == null} → 走 a 步 {@link WbsService#moveTask} (换 parent, 子树级联重编号)</li>
     *   <li>{@code beforeSiblingId != null} → 走 b 步 {@link WbsService#reorderWithinParent} (同级重排)</li>
     * </ul>
     * <p>返回 data = 影响的行数 (a 步: 1+后代数; b 步: 同级 sibling 数)。
     * <p>校验全部下沉到 Service: 跨项目/防环/软删 parent/sibling 越界 一律抛 BusinessException → 400。
     * <p>前端 el-tree 拖拽完成后, 拿这个 N 弹 toast, 然后重新 GET 树刷新。
     */
    // /**
    // * P4-WBS 拖拽重排 (a + b 步) — 移动任务到新父下, 或同 parent 内重排。
    // * <p>body 字段 (两者都给时, <b>beforeSiblingId 优先</b>, 走 b 步):
    // * <ul>
    // *   <li>{@code newParentId} — 目标父 id (null = 顶层)</li>
    // *   <li>{@code beforeSiblingId} — 拖到该 sibling 之前 (null = 末尾 / 走 a 步)</li>
    // * </ul>
    // * <p>dispatch 规则:
    // * <ul>
    // *   <li>{@code beforeSiblingId == null} → 走 a 步 {@link WbsService#moveTask} (换 parent, 子树级联重编号)</li>
    // *   <li>{@code beforeSiblingId != null} → 走 b 步 {@link WbsService#reorderWithinParent} (同级重排)</li>
    // * </ul>
    // * <p>返回 data = 影响的行数 (a 步: 1+后代数; b 步: 同级 sibling 数)。
    // * <p>校验全部下沉到 Service: 跨项目/防环/软删 parent/sibling 越界 一律抛 BusinessException → 400。
    // * <p>前端 el-tree 拖拽完成后, 拿这个 N 弹 toast, 然后重新 GET 树刷新。
    // */
    // @PatchMapping("/tasks/{id}/move")
    // @RequireRoles.Operate
    // @AuditLog(module = "WBS", action = "MOVE")
    // @Operation(summary = "移动/重排任务 (a: 换 parent, b: 同级 reorder; 按 beforeSiblingId dispatch)")
    // public ApiResponse<Integer> moveTask(
    // @PathVariable Long id,
    // @RequestBody(required = false) WbsTaskMoveRequest req) {
    // Long newParentId = req == null ? null : req.newParentId();
    // Long beforeSiblingId = req == null ? null : req.beforeSiblingId();
    // int affected = (beforeSiblingId != null)
    // ? wbsService.reorderWithinParent(id, newParentId, beforeSiblingId)
    // : wbsService.moveTask(id, newParentId);
    // return ApiResponse.ok(affected);
    // }

    @GetMapping("/progress/{projectId}")
    @RequireRoles.Read
    @Operation(summary = "项目级 WBS 进度汇总 (加权进度/工时燃尽比)")
    public ApiResponse<WbsProgressSummary> progress(@PathVariable Long projectId) {
        return ApiResponse.ok(wbsService.progressSummary(projectId));
    }

    // /etc + /burndown endpoints disabled (etc/burndown not yet in WbsService)


    // ---- 资源分配 ----

    @GetMapping("/assignments/by-task/{wbsTaskId}")
    @RequireRoles.Read
    @Operation(summary = "某任务的所有人员分配")
    public ApiResponse<List<WbsAssignmentResponse>> byTask(@PathVariable Long wbsTaskId) {
        return ApiResponse.ok(wbsService.listAssignmentsByTask(wbsTaskId));
    }

    @GetMapping("/assignments/by-user/{userId}")
    @RequireRoles.Read
    @Operation(summary = "某用户的所有任务分配 (资源模块入口)")
    public ApiResponse<List<WbsAssignmentResponse>> byUser(@PathVariable Long userId) {
        return ApiResponse.ok(wbsService.listAssignmentsByUser(userId));
    }

    /**
     * 项目级分配清点 — 给资源矩阵页用 (P3.2)。
     * <p>一次性拉项目下所有 (task, user) 分配, 前端组装成 行=任务 / 列=用户 的矩阵。
     */
    @GetMapping("/assignments/by-project/{projectId}")
    @RequireRoles.Read
    @Operation(summary = "项目下所有 (task,user) 分配 (资源矩阵用)")
    public ApiResponse<List<WbsAssignmentResponse>> byProject(@PathVariable Long projectId) {
        return ApiResponse.ok(wbsService.listAssignmentsByProject(projectId));
    }

    @PostMapping("/assignments")
    @RequireRoles.Operate
    @AuditLog(module = "WBS", action = "ASSIGN_UPSERT")
    @Operation(summary = "新增/更新任务-人员分配 (同 (task,user) 唯一, 自动 upsert)")
    public ApiResponse<WbsAssignmentResponse> upsertAssignment(@Valid @RequestBody WbsAssignmentRequest req) {
        return ApiResponse.ok(WbsAssignmentResponse.from(wbsService.upsertAssignment(req)));
    }

    @DeleteMapping("/assignments/{id}")
    @RequireRoles.Operate
    @AuditLog(module = "WBS", action = "ASSIGN_DELETE", extractResourceId = false)
    @Operation(summary = "软删除分配")
    public ApiResponse<Void> deleteAssignment(@PathVariable Long id) {
        wbsService.deleteAssignment(id);
        return ApiResponse.ok(null);
    }

    // ---- EVM 快照 ----

    @GetMapping("/snapshots/{projectId}")
    @RequireRoles.Read
    @Operation(summary = "项目最近 20 条 EVM 快照")
    public ApiResponse<List<BudgetSnapshotResponse>> snapshots(@PathVariable Long projectId) {
        return ApiResponse.ok(wbsService.listSnapshots(projectId));
    }

    @GetMapping("/snapshots/{projectId}/range")
    @RequireRoles.Read
    @Operation(summary = "项目指定日期区间的 EVM 快照 (趋势图用)")
    public ApiResponse<List<BudgetSnapshotResponse>> snapshotsRange(
            @PathVariable Long projectId,
            @RequestParam String from,
            @RequestParam String to) {
        return ApiResponse.ok(wbsService.snapshotsInRange(
                projectId, LocalDate.parse(from), LocalDate.parse(to)));
    }

    /**
     * 触发一次 EVM 快照 — 手工按钮 / 里程碑完成时。
     * <p>body: { "reason": "..." }
     * <p>DB 实际函数是 {@code pmo.fn_snapshot_evm(project_id, source, operator_user_id)},
     * 但 budget_snapshot 表只有 reason/created_by 字段, 实际使用 operatorUserId 作 created_by。
     */
    @PostMapping("/snapshots/{projectId}/trigger")
    @RequireRoles.Operate
    @AuditLog(module = "EVM", action = "SNAPSHOT")
    @Operation(summary = "触发 EVM 快照 (调 pmo.fn_snapshot_evm SQL 函数)")
    public ApiResponse<BudgetSnapshotResponse> triggerSnapshot(
            @PathVariable Long projectId,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : (String) body.get("reason");
        // source 写死 "MANUAL", operatorUserId 从 auth 上下文取不到 (暂时传 null)
        return ApiResponse.ok(wbsService.snapshotNow(projectId, "MANUAL", reason));
    }

    /**
     * 项目最近 N 天的 EVM 趋势 (P3.1 图表用) — 每天 1 条。
     * <p>days 默认 30, 范围 [1, 365]。同一天多版本取最新那条。
     */
    @GetMapping("/snapshots/{projectId}/trend")
    @RequireRoles.Read
    @Operation(summary = "项目 EVM 趋势 (最近 N 天, 每天 1 条, 升序)")
    public ApiResponse<List<BudgetSnapshotResponse>> snapshotsTrend(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(wbsService.trendSince(projectId, days));
    }

    /**
     * P3.3 WBS 甘特图 — 把项目下 WbsTask 拼成 GanttView 期望的形状, 复用前端组件。
     * <p>返回的 rangeFrom/rangeTo 由后端自动算 (plan 区间 ± 7d, 无任务时回退 today)。
     */
    @GetMapping("/gantt/by-project/{projectId}")
    @RequireRoles.Read
    @Operation(summary = "WBS 任务甘特图数据 (P3.3 复用 GanttView 组件)")
    public ApiResponse<WbsGanttResponse> ganttByProject(@PathVariable Long projectId) {
        return ApiResponse.ok(wbsService.ganttByProject(projectId));
    }

    /**
     * P3.2 WBS 网络图 (依赖关系) + P3.3 关键路径 (CPM 同步算)。
     * <p>节点 = 任务, 边 = 紧前关系 (predecessor)。criticalTaskIds 列出关键路径上的任务 id。
     * <p>前置关系存在环时, 返回的 criticalTaskIds 为空, 节点上 critical 字段全部 false。
     */
    // /network/by-project disabled (networkByProject not yet in WbsService)

    /**
     * P4-WBS 拖拽重排 (c 步) — 同 parent 内按 predecessor 拓扑自动排序。
     * <p>典型场景: 用户维护好 predecessorIds 后, 一键让同级的任务按依赖图从前往后排。
     * <p>行为:
     * <ul>
     *   <li>按 parentId 分组, 每组独立做 Kahn 拓扑 (跨 parent 边忽略)</li>
     *   <li>有环 → 抛 BusinessException(400), 列出环上任务</li>
     *   <li>SUMMARY / MILESTONE 不参与拓扑, 排同级最前</li>
     *   <li>返回 data = 受影响行数 (wbsCode 真的变了的任务数)</li>
     * </ul>
     * <p>前端用法: 项目详情页 "自动排序" 按钮 → POST 此端点 → toast → 重新 GET 树刷新。
     */
    // /projects/{projectId}/auto-reorder disabled (autoReorderWithinParents not yet in WbsService)
}
