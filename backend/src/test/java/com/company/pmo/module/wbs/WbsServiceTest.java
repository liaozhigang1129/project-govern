package com.company.pmo.module.wbs;

import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.project.Project;
import com.company.pmo.module.project.ProjectRepository;
import com.company.pmo.module.wbs.dto.BudgetSnapshotResponse;
import com.company.pmo.module.wbs.dto.WbsAssignmentRequest;
import com.company.pmo.module.wbs.dto.WbsNetworkResponse;
import com.company.pmo.module.wbs.dto.WbsTaskNode;
import com.company.pmo.module.wbs.dto.WbsTaskRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WbsService 单元测试 (P0-A.3 Step 1)。
 *
 * 用 Mockito 隔离 Repository, 验证 Service 层业务规则:
 *  - 树组装 (扁平 → 嵌套 children + depth + path)
 *  - 加权进度 (Σ w*p / Σ w)
 *  - wbs_code 唯一性
 *  - 跨项目保护
 *  - 快照调 SQL 函数
 *  - 资源分配 upsert
 *
 * 不依赖 Spring 容器, 不连接数据库, 跑得快 (<1s/方法)。
 */
@ExtendWith(MockitoExtension.class)
class WbsServiceTest {

    @Mock WbsTaskRepository wbsTaskRepository;
    @Mock WbsAssignmentRepository assignmentRepository;
    @Mock BudgetLineRepository budgetLineRepository;
    @Mock BudgetSnapshotRepository snapshotRepository;
    @Mock ProjectRepository projectRepository;
    @Mock com.company.pmo.module.org.UserRepository userRepository;
    @Mock jakarta.persistence.EntityManager entityManager;
    @Mock jakarta.persistence.Query nativeQuery;

    WbsService wbsService;

    // ============================================================
    // 1.1 树组装: 父+子2行 → 1根 + 1children, depth=1, path=["1","1.1"]
    // ============================================================

    @Test
    @DisplayName("listTreeByProject: 父+子2行 → 1根 + 1children, depth=1, path=[1, 1.1]")
    void listTreeByProject_parentChild_1root1child() {
        // given: 父(id=10, code=1) + 子(id=11, code=1.1, parent=10)
        WbsTask parent = mkTask(10L, 100L, null, "1", "父任务");
        WbsTask child  = mkTask(11L, 100L, 10L, "1.1", "子任务");
        when(wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(100L))
                .thenReturn(List.of(parent, child));

        // when
        List<WbsTaskNode> tree = wbsService.listTreeByProject(100L);

        // then
        assertThat(tree).hasSize(1);
        WbsTaskNode root = tree.get(0);
        assertThat(root.id()).isEqualTo(10L);
        assertThat(root.wbsCode()).isEqualTo("1");
        assertThat(root.depth()).isZero();
        assertThat(root.path()).containsExactly("1");
        assertThat(root.children()).hasSize(1);

        WbsTaskNode leaf = root.children().get(0);
        assertThat(leaf.id()).isEqualTo(11L);
        assertThat(leaf.wbsCode()).isEqualTo("1.1");
        assertThat(leaf.parentId()).isEqualTo(10L);
        assertThat(leaf.depth()).isEqualTo(1);
        assertThat(leaf.path()).containsExactly("1", "1.1");
        assertThat(leaf.children()).isEmpty();
    }

    // ============================================================
    // 1.2 树组装: 孤儿节点 (parentId 指向软删/不存在的父) → 视为根
    // ============================================================

    @Test
    @DisplayName("listTreeByProject: 孤儿节点 (parentId=999 不在结果集) → 视为根")
    void listTreeByProject_orphanBecomesRoot() {
        // given: 一条正常根 + 一条 parentId 指向根本不存在的 id
        WbsTask root       = mkTask(10L, 100L, null, "1", "正常根");
        WbsTask orphan     = mkTask(11L, 100L, 999L, "2", "孤儿节点");
        when(wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(100L))
                .thenReturn(List.of(root, orphan));

        // when
        List<WbsTaskNode> tree = wbsService.listTreeByProject(100L);

        // then: 2 个根 (孤儿不挂任何人, 也不挂 root)
        assertThat(tree).hasSize(2);
        assertThat(tree).extracting(WbsTaskNode::id).containsExactlyInAnyOrder(10L, 11L);
        assertThat(tree).extracting(WbsTaskNode::depth).containsOnly(0);
        // 根不能挂到孤儿名下 — 根的 children 列表都应为空
        assertThat(tree.get(0).children()).isEmpty();
        assertThat(tree.get(1).children()).isEmpty();
    }

    // ============================================================
    // 1.3 加权进度: Σ(w*p) / Σ(w), 各精度
    //   case A: w=1,p=50  + w=2,p=100  → 100*250/3 = 83.33
    //   case B: 全部 NOT_STARTED 0%      → 0
    //   case C: 空项目                    → 0
    //   case D: planHours/actualHours 工时燃尽比
    // ============================================================

    @Test
    @DisplayName("progressSummary: w=1,p=50 + w=2,p=100 → 83.33 (ROUND_HALF_UP 4位)")
    void progressSummary_weighted_basic() {
        WbsTask t1 = mkTask(1L, 100L, null, "1", "A");
        t1.setWeight(1);
        t1.setProgressPct(50);
        WbsTask t2 = mkTask(2L, 100L, null, "2", "B");
        t2.setWeight(2);
        t2.setProgressPct(100);
        when(wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(100L))
                .thenReturn(List.of(t1, t2));

        var s = wbsService.progressSummary(100L);

        // Σ(w*p) = 1*50 + 2*100 = 250; Σ(w) = 3;  100*250/3 = 8333.33...%
        // WbsService 算的是 0-1 的小数 (250/3 = 83.3333... → scale 4 截成 83.3333)
        assertThat(s.weightedProgressPct()).isEqualByComparingTo("83.3333");
        assertThat(s.taskCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("progressSummary: 全部 0% → 加权 = 0")
    void progressSummary_allZero() {
        WbsTask t1 = mkTask(1L, 100L, null, "1", "A");
        WbsTask t2 = mkTask(2L, 100L, null, "2", "B");
        t2.setWeight(5);
        when(wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(100L))
                .thenReturn(List.of(t1, t2));

        var s = wbsService.progressSummary(100L);
        assertThat(s.weightedProgressPct()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("progressSummary: 空项目 → 全 0, 不抛")
    void progressSummary_emptyProject() {
        when(wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(100L))
                .thenReturn(List.of());

        var s = wbsService.progressSummary(100L);
        assertThat(s.taskCount()).isZero();
        assertThat(s.weightedProgressPct()).isEqualByComparingTo("0");
        assertThat(s.totalPlanHours()).isEqualByComparingTo("0");
        assertThat(s.hoursBurnPct()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("progressSummary: 工时燃尽比 actual/plan*100, plan=8h actual=2h → 25.0")
    void progressSummary_hoursBurn() {
        WbsTask t = mkTask(1L, 100L, null, "1", "A");
        t.setPlanHours(new BigDecimal("8.00"));
        t.setActualHours(new BigDecimal("2.00"));
        when(wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(100L))
                .thenReturn(List.of(t));

        var s = wbsService.progressSummary(100L);
        assertThat(s.totalPlanHours()).isEqualByComparingTo("8.00");
        assertThat(s.totalActualHours()).isEqualByComparingTo("2.00");
        assertThat(s.hoursBurnPct()).isEqualByComparingTo("25.0");
    }

    @Test
    @DisplayName("progressSummary: 状态计数 (COMPLETED/IN_PROGRESS/BLOCKED/NOT_STARTED)")
    void progressSummary_statusCounts() {
        WbsTask c1 = mkTask(1L, 100L, null, "1", "已完成");
        c1.setStatus("COMPLETED");
        WbsTask c2 = mkTask(2L, 100L, null, "2", "进行中");
        c2.setStatus("IN_PROGRESS");
        WbsTask c3 = mkTask(3L, 100L, null, "3", "阻塞");
        c3.setStatus("BLOCKED");
        WbsTask c4 = mkTask(4L, 100L, null, "4", "未开始");
        c4.setStatus("NOT_STARTED");
        when(wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(100L))
                .thenReturn(List.of(c1, c2, c3, c4));

        var s = wbsService.progressSummary(100L);
        assertThat(s.completedCount()).isEqualTo(1);
        assertThat(s.inProgressCount()).isEqualTo(1);
        assertThat(s.blockedCount()).isEqualTo(1);
        assertThat(s.notStartedCount()).isEqualTo(1);
        assertThat(s.taskCount()).isEqualTo(4);
    }

    // ============================================================
    // 1.4 save(): wbs_code 重复 → 抛 BusinessException (新建+更新)
    //   case A: 新建时, 项目内 code 已被占 → 抛
    //   case B: 更新时, 改成别人的 code → 抛
    //   case C: 更新时, wbs_code 没变 → 不预检
    // ============================================================

    @Test
    @DisplayName("save: 新建时 wbs_code 已被占 → 抛 BusinessException(包含 code)")
    void save_new_duplicateWbsCodeThrows() {
        // given
        WbsTaskRequest req = new WbsTaskRequest(
                null, 100L, null, "1.1", "新任务",
                "EXECUTION", "NOT_STARTED", null,
                null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 1,
                false, false, null, null, null, null);
        when(projectRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(stubProject()));
        when(wbsTaskRepository.countByProjectIdAndWbsCodeAndDeletedFalse(100L, "1.1"))
                .thenReturn(1L);

        // when + then
        assertThatThrownBy(() -> wbsService.save(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1.1");
    }

    @Test
    @DisplayName("save: 更新时改成别人的 wbs_code → 抛 BusinessException")
    void save_update_changeWbsCodeToExistingThrows() {
        // given: 已存在 id=10, code="1"
        WbsTask existing = mkTask(10L, 100L, null, "1", "老任务");
        when(projectRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(stubProject()));
        when(wbsTaskRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(wbsTaskRepository.countByProjectIdAndWbsCodeAndDeletedFalse(100L, "1.1"))
                .thenReturn(1L);

        WbsTaskRequest req = new WbsTaskRequest(
                10L, 100L, null, "1.1", "改名",
                "EXECUTION", "NOT_STARTED", null,
                null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 1,
                false, false, null, null, null, null);

        // when + then
        assertThatThrownBy(() -> wbsService.save(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1.1");
    }

    @Test
    @DisplayName("save: 更新时 wbs_code 不变 → 不预检, 直接保存")
    void save_update_sameWbsCodeNoCheck() {
        // given
        WbsTask existing = mkTask(10L, 100L, null, "1.1", "老任务");
        when(projectRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(stubProject()));
        when(wbsTaskRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(wbsTaskRepository.save(any(WbsTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(wbsTaskRepository.computeWeightedProgressPct(100L)).thenReturn(0);

        WbsTaskRequest req = new WbsTaskRequest(
                10L, 100L, null, "1.1", "改名",
                "EXECUTION", "NOT_STARTED", null,
                null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 1,
                false, false, null, null, null, null);

        // when
        WbsTask saved = wbsService.save(req);

        // then: 没调过 countBy...AndDeletedFalse (wbs_code 没变,跳过预检)
        verify(wbsTaskRepository, never())
                .countByProjectIdAndWbsCodeAndDeletedFalse(anyLong(), anyString());
        assertThat(saved.getName()).isEqualTo("改名");
        assertThat(saved.getWbsCode()).isEqualTo("1.1");
    }

    // ============================================================
    // 1.5 save(): 把任务改到别的项目下 → 抛 BusinessException
    // ============================================================

    @Test
    @DisplayName("save: 更新时把任务改到别的项目 (req.projectId ≠ t.projectId) → 抛")
    void save_update_crossProjectThrows() {
        // given: 已存在 id=10, projectId=100
        WbsTask existing = mkTask(10L, 100L, null, "1.1", "原项目任务");
        when(projectRepository.findByIdAndDeletedFalse(200L)).thenReturn(Optional.of(stubProject()));
        when(wbsTaskRepository.findById(10L)).thenReturn(Optional.of(existing));

        WbsTaskRequest req = new WbsTaskRequest(
                10L, 200L, null, "1.1", "搬到 200",
                "EXECUTION", "NOT_STARTED", null,
                null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 1,
                false, false, null, null, null, null);

        // when + then
        assertThatThrownBy(() -> wbsService.save(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能把任务改到别的项目下");
    }

    @Test
    @DisplayName("save: projectId=null → validateProject 抛 'projectId 不能为空'")
    void save_nullProjectIdThrows() {
        WbsTaskRequest req = new WbsTaskRequest(
                null, null, null, "1.1", "无项目",
                "EXECUTION", "NOT_STARTED", null,
                null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 1,
                false, false, null, null, null, null);

        assertThatThrownBy(() -> wbsService.save(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("projectId");
    }

    @Test
    @DisplayName("save: projectId 不存在 → 'Project not found'")
    void save_projectNotFoundThrows() {
        WbsTaskRequest req = new WbsTaskRequest(
                null, 999L, null, "1.1", "不存在的项目",
                "EXECUTION", "NOT_STARTED", null,
                null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 1,
                false, false, null, null, null, null);
        when(projectRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wbsService.save(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("999");
    }

    // ============================================================
    // 1.7 upsertAssignment: 同 (wbsTaskId, userId) 已存在 → 更新, 不存在 → 新建
    //   case A: 已存在 → 更新 role/plannedHours/actualHours
    //   case B: 不存在 → 新建
    //   case C: role=null → 默认 "DOER"
    //   case D: actualHours=null → 默认 0
    // ============================================================

    @Test
    @DisplayName("upsertAssignment: 已存在 (task,user) → 更新字段, 不新建")
    void upsertAssignment_existing_updateFields() {
        WbsAssignment existing = new WbsAssignment();
        existing.setId(50L);
        existing.setWbsTaskId(10L);
        existing.setUserId(7L);
        existing.setRole("DOER");
        existing.setPlannedHours(new BigDecimal("4.00"));
        existing.setActualHours(new BigDecimal("0.00"));
        when(assignmentRepository.findByWbsTaskIdAndUserIdAndDeletedFalse(10L, 7L))
                .thenReturn(Optional.of(existing));
        when(assignmentRepository.save(any(WbsAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        WbsAssignmentRequest req = new WbsAssignmentRequest(
                null, 10L, 7L, "REVIEWER",
                new BigDecimal("8.00"), new BigDecimal("3.00"),
                null, null);

        WbsAssignment result = wbsService.upsertAssignment(req);

        assertThat(result.getId()).isEqualTo(50L);  // id 没变, 没新建
        assertThat(result.getRole()).isEqualTo("REVIEWER");
        assertThat(result.getPlannedHours()).isEqualByComparingTo("8.00");
        assertThat(result.getActualHours()).isEqualByComparingTo("3.00");
        // 验证: save 调了一次, findById 没调 (没新建实体)
        verify(assignmentRepository).save(any(WbsAssignment.class));
    }

    @Test
    @DisplayName("upsertAssignment: 不存在 → 新建, 字段都填上")
    void upsertAssignment_new_create() {
        when(assignmentRepository.findByWbsTaskIdAndUserIdAndDeletedFalse(10L, 7L))
                .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(WbsAssignment.class))).thenAnswer(inv -> {
            WbsAssignment a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        WbsAssignmentRequest req = new WbsAssignmentRequest(
                null, 10L, 7L, "LEAD",
                new BigDecimal("16.00"), null,
                null, null);

        WbsAssignment result = wbsService.upsertAssignment(req);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getWbsTaskId()).isEqualTo(10L);
        assertThat(result.getUserId()).isEqualTo(7L);
        assertThat(result.getRole()).isEqualTo("LEAD");
        assertThat(result.getPlannedHours()).isEqualByComparingTo("16.00");
        assertThat(result.getActualHours()).isEqualByComparingTo("0.00");  // null → 0
    }

    @Test
    @DisplayName("upsertAssignment: role=null → 默认 'DOER'")
    void upsertAssignment_roleNull_defaultsToDOER() {
        when(assignmentRepository.findByWbsTaskIdAndUserIdAndDeletedFalse(10L, 7L))
                .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(WbsAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        WbsAssignmentRequest req = new WbsAssignmentRequest(
                null, 10L, 7L, null,
                new BigDecimal("4.00"), null, null, null);

        WbsAssignment result = wbsService.upsertAssignment(req);

        assertThat(result.getRole()).isEqualTo("DOER");
    }

    // ============================================================
    // 1.8 softDelete: 软删任务 + recompute project progress
    //   case A: 正常软删 → deleted=true, 调 save + recompute
    //   case B: id 不存在 → 抛 404
    //   case C: 已被软删 → 抛 404 (getById 过滤 deleted)
    // ============================================================

    @Test
    @DisplayName("softDelete: 正常路径 → deleted=true, save 一次, recompute 一次")
    void softDelete_happyPath() {
        WbsTask t = mkTask(10L, 100L, null, "1.1", "待删");
        when(wbsTaskRepository.findById(10L)).thenReturn(Optional.of(t));
        when(wbsTaskRepository.save(any(WbsTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(wbsTaskRepository.computeWeightedProgressPct(100L)).thenReturn(0);
        when(projectRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(stubProject()));

        wbsService.softDelete(10L);

        assertThat(t.isDeleted()).isTrue();
        verify(wbsTaskRepository).save(t);
        verify(wbsTaskRepository).computeWeightedProgressPct(100L);
    }

    @Test
    @DisplayName("softDelete: id 不存在 → 抛 BusinessException(404)")
    void softDelete_notFoundThrows() {
        when(wbsTaskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wbsService.softDelete(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(404);
    }

    @Test
    @DisplayName("softDelete: 已被软删 → getById 过滤 → 抛 404")
    void softDelete_alreadyDeletedThrows() {
        WbsTask t = mkTask(10L, 100L, null, "1.1", "已删");
        t.setDeleted(true);
        when(wbsTaskRepository.findById(10L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> wbsService.softDelete(10L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(404);
    }

    // ============================================================
    // 1.6 snapshotNow: 调 pmo.fn_snapshot_evm SQL 函数 + 拉最新一条快照
    //   case A: 正常 → 返回最新 BudgetSnapshotResponse
    //   case B: source=null/blank → 默认 "MANUAL"
    //   case C: projectId 不存在 → 抛 (validateProject)
    //   case D: SQL 函数执行后没记录 → 抛
    // ============================================================

    @Test
    @DisplayName("snapshotNow: 正常路径 → 调 fn_snapshot_evm + 返最新快照")
    void snapshotNow_happyPath() throws Exception {
        Long projectId = 100L;
        BudgetSnapshot latest = new BudgetSnapshot();
        latest.setId(99L);
        latest.setProjectId(projectId);
        latest.setSnapshotDate(java.time.LocalDate.of(2025, 1, 15));
        latest.setVersion(1);
        latest.setReason("人工触发");
        latest.setBac(new BigDecimal("100000"));
        latest.setPv(new BigDecimal("50000"));
        latest.setEv(new BigDecimal("40000"));
        latest.setAc(new BigDecimal("45000"));
        latest.setCpi(new BigDecimal("0.889"));
        latest.setSpi(new BigDecimal("0.800"));
        latest.setEac(new BigDecimal("112500"));
        latest.setEtc(new BigDecimal("67500"));
        latest.setVac(new BigDecimal("-12500"));

        stubEntityManagerChain();
        when(projectRepository.findByIdAndDeletedFalse(projectId)).thenReturn(Optional.of(stubProject()));
        when(snapshotRepository.findLatestByProject(projectId)).thenReturn(List.of(latest));

        BudgetSnapshotResponse resp = wbsService.snapshotNow(projectId, "MANUAL", "人工触发");

        // 验证: 调了 native query, 拿的是函数返回值
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("pmo.fn_snapshot_evm");
        // 验证: response 字段映射正确
        assertThat(resp.id()).isEqualTo(99L);
        assertThat(resp.cpi()).isEqualByComparingTo("0.889");
        assertThat(resp.spi()).isEqualByComparingTo("0.800");
    }

    @Test
    @DisplayName("snapshotNow: source=null → 默认 'MANUAL'")
    void snapshotNow_sourceNull_defaultsToManual() {
        stubEntityManagerChain();
        when(projectRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(stubProject()));
        BudgetSnapshot snap = mkSnapshot(1L, 100L);
        when(snapshotRepository.findLatestByProject(100L)).thenReturn(List.of(snap));

        wbsService.snapshotNow(100L, null, "test");

        // 第二次 setParameter(2, ...) 拿到的应该是 "MANUAL"
        ArgumentCaptor<Object> param2 = ArgumentCaptor.forClass(Object.class);
        verify(nativeQuery).setParameter(org.mockito.ArgumentMatchers.eq(2), param2.capture());
        assertThat(param2.getValue()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("snapshotNow: source=空串 → 默认 'MANUAL'")
    void snapshotNow_sourceBlank_defaultsToManual() {
        stubEntityManagerChain();
        when(projectRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(stubProject()));
        when(snapshotRepository.findLatestByProject(100L)).thenReturn(List.of(mkSnapshot(1L, 100L)));

        wbsService.snapshotNow(100L, "  ", "test");

        ArgumentCaptor<Object> param2 = ArgumentCaptor.forClass(Object.class);
        verify(nativeQuery).setParameter(org.mockito.ArgumentMatchers.eq(2), param2.capture());
        assertThat(param2.getValue()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("snapshotNow: projectId 不存在 → validateProject 抛")
    void snapshotNow_projectNotFoundThrows() {
        when(projectRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wbsService.snapshotNow(999L, "MANUAL", "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("snapshotNow: SQL 函数执行后没记录 → 抛 'Snapshot 函数执行后未找到记录'")
    void snapshotNow_noRecordAfterFnThrows() {
        stubEntityManagerChain();
        when(projectRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(stubProject()));
        when(snapshotRepository.findLatestByProject(100L)).thenReturn(List.of());

        assertThatThrownBy(() -> wbsService.snapshotNow(100L, "MANUAL", "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Snapshot 函数执行后未找到记录");
    }

    // ============================================================
    // 1.7 trendSince: 拉最近 N 天趋势 (P3.1 图表)
    //   case A: 正常 → Repository 返 3 条, Service 透传
    //   case B: days=0/负数 → 兜底 1
    //   case C: days>365 → 兜底 365
    // ============================================================

    @Test
    @DisplayName("trendSince: 默认 30 天, Repository 返的列表透传为 Response")
    void trendSince_happyPath() {
        when(snapshotRepository.findTrendSince(eq(100L), any(java.time.LocalDate.class)))
                .thenReturn(java.util.List.of(mkSnapshot(1L, 100L), mkSnapshot(2L, 100L), mkSnapshot(3L, 100L)));

        List<BudgetSnapshotResponse> result = wbsService.trendSince(100L, 30);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(BudgetSnapshotResponse::id)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("trendSince: days=0 → 兜底为 1 (since=今天)")
    void trendSince_daysZero_clampsToOne() {
        when(snapshotRepository.findTrendSince(eq(100L), any(java.time.LocalDate.class)))
                .thenReturn(List.of(mkSnapshot(1L, 100L)));

        wbsService.trendSince(100L, 0);

        // since 应该是今天 (用 fixed clock 不靠谱, 这里用 [today-1, today+1] 区间断言, 跨时区容差)
        ArgumentCaptor<java.time.LocalDate> sinceCaptor = ArgumentCaptor.forClass(java.time.LocalDate.class);
        verify(snapshotRepository).findTrendSince(eq(100L), sinceCaptor.capture());
        java.time.LocalDate today = java.time.LocalDate.now();
        assertThat(sinceCaptor.getValue()).isBetween(today.minusDays(1), today.plusDays(1));
    }

    @Test
    @DisplayName("trendSince: days=1000 → 兜底为 365 (上限)")
    void trendSince_daysTooBig_clampsTo365() {
        when(snapshotRepository.findTrendSince(eq(100L), any(java.time.LocalDate.class)))
                .thenReturn(List.of());

        wbsService.trendSince(100L, 1000);

        // since 应该是 today - 365 (同样容差)
        ArgumentCaptor<java.time.LocalDate> sinceCaptor = ArgumentCaptor.forClass(java.time.LocalDate.class);
        verify(snapshotRepository).findTrendSince(eq(100L), sinceCaptor.capture());
        java.time.LocalDate today = java.time.LocalDate.now();
        assertThat(sinceCaptor.getValue())
                .isBetween(today.minusDays(366), today.minusDays(364));
    }

    // ============================================================
    // helpers
    // ============================================================

    private static WbsTask mkTask(Long id, Long projectId, Long parentId, String code, String name) {
        WbsTask t = new WbsTask();
        t.setId(id);
        t.setProjectId(projectId);
        t.setParentId(parentId);
        t.setWbsCode(code);
        t.setName(name);
        t.setTaskType("EXECUTION");
        t.setStatus("NOT_STARTED");
        t.setWeight(1);
        t.setProgressPct(0);
        t.setPlanHours(BigDecimal.ZERO);
        t.setActualHours(BigDecimal.ZERO);
        t.setPredecessorIds(new Long[0]);
        return t;
    }

    /** P3.2 网络图测试用 — 带 plan 区间 + 可选紧前的任务 */
    private static WbsTask mkTaskWithRange(Long id, String code, String name,
                                           LocalDate start, LocalDate end,
                                           Long... predecessorIds) {
        WbsTask t = mkTask(id, 100L, null, code, name);
        t.setPlanStartDate(start);
        t.setPlanEndDate(end);
        t.setPredecessorIds(predecessorIds == null ? new Long[0] : predecessorIds);
        return t;
    }

    /** Project stub — 只塞 id 就行, validateProject 只调 findByIdAndDeletedFalse */
    private static Project stubProject() {
        Project p = new Project();
        p.setId(100L);
        p.setCode("P-TEST");
        p.setName("测试项目");
        return p;
    }

    /** 手动注入 — WbsService 用的不是构造器注入 entityManager, 是字段, @InjectMocks 没法塞 */
    @org.junit.jupiter.api.BeforeEach
    void initService() {
        wbsService = new WbsService(
                wbsTaskRepository, assignmentRepository, budgetLineRepository,
                snapshotRepository, projectRepository, userRepository);
        try {
            java.lang.reflect.Field f = WbsService.class.getDeclaredField("entityManager");
            f.setAccessible(true);
            f.set(wbsService, entityManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * stub EntityManager.createNativeQuery(...)
     *    .setParameter(1, projectId)  // Query return self
     *    .setParameter(2, source)     // Query return self
     *    .setParameter(3, 1)          // Query return self
     *    .getSingleResult()           // Object = null
     */
    private void stubEntityManagerChain() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(null);
    }

    private static BudgetSnapshot mkSnapshot(Long id, Long projectId) {
        BudgetSnapshot s = new BudgetSnapshot();
        s.setId(id);
        s.setProjectId(projectId);
        s.setSnapshotDate(java.time.LocalDate.now());
        s.setVersion(1);
        s.setBac(BigDecimal.ZERO);
        s.setPv(BigDecimal.ZERO);
        s.setEv(BigDecimal.ZERO);
        s.setAc(BigDecimal.ZERO);
        s.setCpi(BigDecimal.ONE);
        s.setSpi(BigDecimal.ONE);
        s.setEac(BigDecimal.ZERO);
        s.setEtc(BigDecimal.ZERO);
        s.setVac(BigDecimal.ZERO);
        return s;
    }

}
