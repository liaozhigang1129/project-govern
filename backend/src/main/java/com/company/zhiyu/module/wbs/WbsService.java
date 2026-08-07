package com.company.zhiyu.module.wbs;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.module.org.AppUser;
import com.company.zhiyu.module.org.UserRepository;
import com.company.zhiyu.module.project.ProjectRepository;
import com.company.zhiyu.module.wbs.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WBS / EVM / 资源分配 业务逻辑。
 * <p>设计要点:
 * <ul>
 *   <li>树组装在 Service 层完成(扁平 List → 嵌套 children),避免 Controller 拼装</li>
 *   <li>加权进度用 JPQL 一次聚合,避免 LAZY status + N+1</li>
 *   <li>预算快照 INSERT 通过调用 SQL 函数 {@code pmo.fn_snapshot_evm}, 触发器已禁 UPDATE/DELETE</li>
 *   <li>predecessorIds 走 JSON 字符串, 简单可靠; 写入时由 Service 序列化</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WbsService {

    private final WbsTaskRepository wbsTaskRepository;
    private final WbsAssignmentRepository assignmentRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final BudgetSnapshotRepository snapshotRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    // ============================================================
    // WBS 任务
    // ============================================================

    /** 拉某项目全树(平铺 List + 嵌套 children, 一并返回, 前端直接用) */
    @Transactional(readOnly = true)
    public List<WbsTaskNode> listTreeByProject(Long projectId) {
        List<WbsTask> all = wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectId);
        return buildTree(all);
    }

    /** 拉所有任务扁平 List (供下拉/搜索/导入用) */
    @Transactional(readOnly = true)
    public List<WbsTaskNode> listFlatByProject(Long projectId) {
        return wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectId).stream()
                .map(t -> WbsTaskNode.leaf(t, 0, List.of(t.getWbsCode())))
                .toList();
    }

    /** 按 id 取单个任务(给编辑/详情用) */
    @Transactional(readOnly = true)
    public WbsTask getById(Long id) {
        return wbsTaskRepository.findById(id)
                .filter(t -> !t.isDeleted())
                .orElseThrow(() -> new BusinessException(404, "WbsTask not found: " + id));
    }

    /** 新建/更新 (id=null/0 新建, id>0 更新) */
    @Transactional
    public WbsTask save(WbsTaskRequest req) {
        validateProject(req.projectId());
        // 防御: 前端可能在 dialog 重置时残留 id=0,统一视为新建
        Long incomingId = (req.id() != null && req.id() > 0) ? req.id() : null;
        WbsTask t = (incomingId == null)
                ? new WbsTask()
                : wbsTaskRepository.findById(incomingId)
                        .filter(task -> !task.isDeleted())
                        .orElseThrow(() -> new BusinessException(404, "WbsTask not found: " + req.id()));
        if (incomingId != null && !t.getProjectId().equals(req.projectId())) {
            throw new BusinessException("不能把任务改到别的项目下");
        }

        // wbs_code 唯一性预检(更新时排除自己)
        if (req.id() == null || !req.wbsCode().equals(t.getWbsCode())) {
            if (wbsTaskRepository.countByProjectIdAndWbsCodeAndDeletedFalse(
                    req.projectId(), req.wbsCode()) > 0) {
                throw new BusinessException("WBS 编码已存在: " + req.wbsCode());
            }
        }

        t.setProjectId(req.projectId());
        t.setParentId(req.parentId());
        t.setWbsCode(req.wbsCode());
        t.setName(req.name());
        t.setTaskType(req.taskTypeOrDefault());
        t.setStatus(req.statusOrDefault());
        t.setOwnerUserId(req.ownerUserId());
        t.setPlanStartDate(req.planStartDate());
        t.setPlanEndDate(req.planEndDate());
        t.setActualStartDate(req.actualStartDate());
        t.setActualEndDate(req.actualEndDate());
        t.setPlanHours(req.planHours() == null ? BigDecimal.ZERO : req.planHours());
        t.setActualHours(req.actualHours() == null ? BigDecimal.ZERO : req.actualHours());
        t.setProgressPct(req.progressOrDefault());
        t.setWeight(req.weightOrDefault());
        t.setCritical(req.criticalOrDefault());
        t.setMilestone(req.milestoneOrDefault());
        t.setMilestoneId(req.milestoneId());
        t.setPredecessorIds(toIdArray(req.predecessorIds()));
        t.setDeliverable(req.deliverable());
        t.setRemark(req.remark());

        // COMPLETED 时不再自动填 completed_at(该列已删除, status 字段本身足够)

        WbsTask saved = wbsTaskRepository.save(t);

        // 写完任务后重算项目级加权进度(V2.5 spec)
        recomputeAndPersistProjectProgress(saved.getProjectId());

        return saved;
    }

    /** 软删除(同时把子任务一并软删? 不, 留给用户手动删子任务, 避免误删) */
    @Transactional
    public void softDelete(Long id) {
        WbsTask t = getById(id);
        // V2.5: 删之前先检查子任务,有则拒绝
        List<WbsTask> children = wbsTaskRepository.findByParentIdAndDeletedFalseOrderByIdAsc(id);
        if (!children.isEmpty()) {
            String childNames = children.stream().limit(3)
                    .map(c -> c.getWbsCode() + " " + c.getName())
                    .collect(Collectors.joining(", "));
            String suffix = children.size() > 3 ? " 等 " + children.size() + " 个子任务" : "";
            throw new BusinessException("请先删除子任务: " + childNames + suffix);
        }
        t.setDeleted(true);
        wbsTaskRepository.save(t);
        recomputeAndPersistProjectProgress(t.getProjectId());
    }

    /** 项目级加权进度(对齐 v_wbs_progress_summary) */
    @Transactional(readOnly = true)
    public WbsProgressSummary progressSummary(Long projectId) {
        List<WbsTask> all = wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectId);
        long total = all.size();
        long completed = all.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long inProgress = all.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
        long blocked = all.stream().filter(t -> "BLOCKED".equals(t.getStatus())).count();
        long notStarted = all.stream().filter(t -> "NOT_STARTED".equals(t.getStatus())).count();
        long critical = all.stream().filter(WbsTask::isCritical).count();
        long milestone = all.stream().filter(WbsTask::isMilestone).count();

        // weight 是 Integer (1-10), 累加成 long 再转 BigDecimal, 避免重量 Integer*Integer
        long weightSumLong = all.stream()
                .map(t -> t.getWeight() == null ? 0 : t.getWeight())
                .mapToLong(Integer::intValue).sum();
        BigDecimal weightSum = BigDecimal.valueOf(weightSumLong);
        BigDecimal weightedNum = all.stream()
                .map(t -> {
                    int w = t.getWeight() == null ? 0 : t.getWeight();
                    int p = t.getProgressPct() == null ? 0 : t.getProgressPct();
                    return BigDecimal.valueOf((long) w * p);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weightedPct = weightSum.signum() == 0
                ? BigDecimal.ZERO
                : weightedNum.divide(weightSum, 4, RoundingMode.HALF_UP);

        BigDecimal planSum = all.stream().map(WbsTask::getPlanHours).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actualSum = all.stream().map(WbsTask::getActualHours).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal burnPct = planSum.signum() == 0
                ? BigDecimal.ZERO
                : actualSum.multiply(BigDecimal.valueOf(100))
                        .divide(planSum, 1, RoundingMode.HALF_UP);

        return new WbsProgressSummary(
                projectId, total, completed, inProgress, blocked, notStarted,
                critical, milestone,
                weightedPct, planSum, actualSum, burnPct
        );
    }

    // ============================================================
    // 资源分配
    // ============================================================

    @Transactional(readOnly = true)
    public List<WbsAssignmentResponse> listAssignmentsByTask(Long wbsTaskId) {
        return assignmentRepository.findByWbsTaskIdAndDeletedFalse(wbsTaskId).stream()
                .map(WbsAssignmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WbsAssignmentResponse> listAssignmentsByUser(Long userId) {
        return assignmentRepository.findByUserId(userId).stream()
                .map(WbsAssignmentResponse::from)
                .toList();
    }

    /**
     * 项目级分配清点 — 给资源矩阵页用。
     * <p>把扁平 assignments 按 (taskId, userId) 二维化, 一次拉全项目分配。
     */
    @Transactional(readOnly = true)
    public List<WbsAssignmentResponse> listAssignmentsByProject(Long projectId) {
        return assignmentRepository.findByProjectId(projectId).stream()
                .map(WbsAssignmentResponse::from)
                .toList();
    }

    @Transactional
    public WbsAssignment upsertAssignment(WbsAssignmentRequest req) {
        // upsert: 同 (wbsTaskId, userId) 已存在则更新, 不存在则新建
        WbsAssignment a = assignmentRepository
                .findByWbsTaskIdAndUserIdAndDeletedFalse(req.wbsTaskId(), req.userId())
                .orElseGet(() -> {
                    WbsAssignment n = new WbsAssignment();
                    n.setWbsTaskId(req.wbsTaskId());
                    n.setUserId(req.userId());
                    return n;
                });
        a.setRole(req.roleOrDefault());
        a.setPlannedHours(req.plannedHours());
        a.setActualHours(req.actualHours() == null ? BigDecimal.ZERO : req.actualHours());
        a.setStartDate(req.startDate());
        a.setEndDate(req.endDate());
        return assignmentRepository.save(a);
    }

    @Transactional
    public void deleteAssignment(Long id) {
        WbsAssignment a = assignmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Assignment not found: " + id));
        a.setDeleted(true);
        assignmentRepository.save(a);
    }

    // ============================================================
    // 预算/快照
    // ============================================================

    @Transactional(readOnly = true)
    public List<BudgetSnapshotResponse> listSnapshots(Long projectId) {
        return snapshotRepository.findLatestByProject(projectId).stream()
                .limit(20)  // 默认拉最近 20 条
                .map(BudgetSnapshotResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BudgetSnapshotResponse> snapshotsInRange(Long projectId,
                                                          java.time.LocalDate from,
                                                          java.time.LocalDate to) {
        return snapshotRepository.findByProjectIdAndDateRange(projectId, from, to).stream()
                .map(BudgetSnapshotResponse::from)
                .toList();
    }

    /**
     * 拉项目最近 N 天趋势 (每天 1 条), 返扁平 DTO 列表给前端画图。
     * <p>天数默认 30, 范围检查: 1 <= days <= 365。
     * <p>同一天多条快照时只留 version 最大 (最新) 那条, 已在 Repository 层做掉。
     */
    @Transactional(readOnly = true)
    public List<BudgetSnapshotResponse> trendSince(Long projectId, int days) {
        int safeDays = Math.max(1, Math.min(365, days));
        java.time.LocalDate since = java.time.LocalDate.now().minusDays(safeDays);
        return snapshotRepository.findTrendSince(projectId, since).stream()
                .map(BudgetSnapshotResponse::from)
                .toList();
    }

    // ============================================================
    // P3.3 WBS 甘特图
    // ============================================================

    /**
     * 拉项目的 WBS 任务, 拼成甘特图响应 (给前端 GanttView 复用)。
     * <p>行为:
     * <ul>
     *   <li>一次性拉项目所有未软删的 WbsTask (按 wbsCode 升序, 即深度优先)</li>
     *   <li>批量查出 ownerUserId 对应的 AppUser, 注入 ownerName</li>
     *   <li>坐标轴: 任务 planStart/End 的最早/最晚 ± 7d; 无任务时回退 today ± 30d/60d</li>
     *   <li>只返有 plan 区间的任务 (没区间画不出来)</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public WbsGanttResponse ganttByProject(Long projectId) {
        validateProject(projectId);
        List<WbsTask> all = wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectId);

        // 批量查 owner 名字 (1 次 IN 查询, 避免 N+1)
        Set<Long> ownerIds = new HashSet<>();
        for (WbsTask t : all) if (t.getOwnerUserId() != null) ownerIds.add(t.getOwnerUserId());
        Map<Long, String> ownerNameById = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            for (AppUser u : userRepository.findAllById(ownerIds)) {
                ownerNameById.put(u.getId(), u.getFullName() != null ? u.getFullName() : u.getUsername());
            }
        }

        List<WbsGanttRow> rows = new ArrayList<>();
        LocalDate minStart = null, maxEnd = null;
        for (WbsTask t : all) {
            LocalDate s = t.getPlanStartDate();
            LocalDate e = t.getPlanEndDate();
            if (s == null || e == null) continue;
            if (minStart == null || s.isBefore(minStart)) minStart = s;
            if (maxEnd   == null || e.isAfter(maxEnd))    maxEnd   = e;
            rows.add(new WbsGanttRow(
                    t.getId(), t.getWbsCode(), t.getName(),
                    0, t.getParentId(),
                    t.getTaskType(), t.getStatus(),
                    t.getOwnerUserId(),
                    t.getOwnerUserId() != null ? ownerNameById.get(t.getOwnerUserId()) : null,
                    s, e,
                    t.getActualStartDate(), t.getActualEndDate(),
                    t.getProgressPct(), t.getWeight(),
                    t.isCritical(), t.isMilestone(),
                    t.getPlanHours(), t.getActualHours()
            ));
        }

        LocalDate today = LocalDate.now();
        String rangeFrom, rangeTo;
        if (minStart != null && maxEnd != null) {
            rangeFrom = minStart.minusDays(7).toString();
            rangeTo   = maxEnd.plusDays(7).toString();
        } else {
            rangeFrom = today.minusDays(30).toString();
            rangeTo   = today.plusDays(60).toString();
        }
        return new WbsGanttResponse(projectId, rangeFrom, rangeTo, rows.size(), rows);
    }

    /**
     * 触发一次 EVM 快照(手工按钮 / 里程碑完成时调用)。
     * <p>实际逻辑: 通过 EntityManager 调用 SQL 函数 {@code pmo.fn_snapshot_evm}。
     * 由于 service 层只有 Spring Data JPA, 这里用注入 EntityManager 走 native query。
     * <p>触发器已禁 UPDATE/DELETE, 但本操作是 INSERT, 不影响。
     */
    @Transactional
    public BudgetSnapshotResponse snapshotNow(Long projectId, String source, String reason) {
        validateProject(projectId);
        if (source == null || source.isBlank()) source = "MANUAL";
        // 用 native query 调用 SQL 函数
        jakarta.persistence.EntityManager em = entityManager;
        em.createNativeQuery("SELECT pmo.fn_snapshot_evm(?, ?, ?)")
                .setParameter(1, projectId)
                .setParameter(2, source)
                .setParameter(3, 1)   // operator_user_id: 暂时用 1 (admin), 后续接 auth 上下文
                .getSingleResult();
        // 拉最新一条
        BudgetSnapshot latest = snapshotRepository.findLatestByProject(projectId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("Snapshot 函数执行后未找到记录"));
        return BudgetSnapshotResponse.from(latest);
    }

    // ============================================================
    // 私有
    // ============================================================

    private void validateProject(Long projectId) {
        if (projectId == null) throw new BusinessException("projectId 不能为空");
        if (projectRepository.findByIdAndDeletedFalse(projectId).isEmpty()) {
            throw new BusinessException("Project not found: " + projectId);
        }
    }

    /** 树组装: O(n) 单次遍历, 用 id→node map 索引 */
    private List<WbsTaskNode> buildTree(List<WbsTask> flat) {
        // path 累积需要父子关系, 先建 wbs_code → node 的引用, 便于构造 path
        Map<Long, WbsTaskNode> byId = new LinkedHashMap<>();
        for (WbsTask t : flat) {
            WbsTaskNode n = WbsTaskNode.leaf(t, 0, new ArrayList<>(List.of(t.getWbsCode())));
            byId.put(t.getId(), n);
        }
        // 组装父子 + depth (深度=2 步: 先建索引, 再挂孩子)
        List<WbsTaskNode> roots = new ArrayList<>();
        for (WbsTask t : flat) {
            WbsTaskNode node = byId.get(t.getId());
            if (t.getParentId() == null) {
                roots.add(node);
            } else {
                WbsTaskNode parent = byId.get(t.getParentId());
                if (parent != null) {
                    // 重要: 先把当前 node 的引用加到 parent.children,
                    //       然后再 new 一个带 depth+1 / 完整 path 的 node 替换 byId 里的引用
                    // 这样 parent 拿到的是旧的 node (depth=0, path=[ownCode]) — 这是 BUG!
                    // 修正: 必须在 add 之前先构造带正确 depth/path 的新 node
                    WbsTaskNode childWithDepth = new WbsTaskNode(
                            node.id(), node.projectId(), node.parentId(),
                            node.wbsCode(), node.name(), node.taskType(), node.status(),
                            node.ownerUserId(), node.planStartDate(), node.planEndDate(),
                            node.actualStartDate(), node.actualEndDate(),
                            node.planHours(), node.actualHours(),
                            node.progressPct(), node.weight(),
                            node.critical(), node.milestone(), node.milestoneId(),
                            node.predecessorIds(), node.deliverable(), node.remark(),
                            node.createdAt(), node.updatedAt(),
                            parent.depth() + 1,
                            concatPath(parent.path(), node.wbsCode()),
                            node.children()
                    );
                    // 用新 node 替换 byId 引用, 并挂到 parent
                    byId.put(t.getId(), childWithDepth);
                    parent.children().add(childWithDepth);
                } else {
                    // 父节点不存在(可能软删), 视为根
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    private static List<String> concatPath(List<String> parentPath, String ownCode) {
        List<String> p = new ArrayList<>(parentPath);
        p.add(ownCode);
        return p;
    }

    private Long[] toIdArray(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new Long[0];
        return ids.toArray(new Long[0]);
    }

    private void recomputeAndPersistProjectProgress(Long projectId) {
        Integer pct = wbsTaskRepository.computeWeightedProgressPct(projectId);
        int raw = pct == null ? 0 : pct;
        int newPct = Math.max(0, Math.min(100, raw));  // 防御越界 (SQL: weight×progress 可能 > 100)
        projectRepository.findByIdAndDeletedFalse(projectId).ifPresent(p -> {
            if (p.getProgressPct() != newPct) {
                p.setProgressPct(newPct);
            }
        });
    }

    // ---- 注入 EntityManager (供 snapshotNow 调 SQL 函数) ----
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
}