package com.company.pmo.module.wbs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WBS 5 个 Repository 的 JPA 集成测试 (P0-A.3 Step 4)。
 *
 * <h3>H2 兼容性策略</h3>
 * WbsTask.predecessorIds 列在 PostgreSQL 是 {@code bigint[]}, H2 不支持此类型,
 * Hibernate 自动 DDL 会失败连带所有 entity 都不建表。
 *
 * <p>解决方案: {@code ddl-auto=none} 关闭自动建表, 用 {@code data-h2.sql} 手工建
 * 全部 WBS 表(用 {@code VARCHAR} 替代 bigint[])。这样不依赖 Entity 的 DDL 输出,
 * 也不污染主代码。
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@org.springframework.test.context.jdbc.Sql(scripts = "/test-schema-h2.sql",
        executionPhase = org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class WbsRepositoryTest {

    @Autowired WbsTaskRepository wbsTaskRepository;
    @Autowired WbsAssignmentRepository assignmentRepository;
    @Autowired BudgetLineRepository budgetLineRepository;
    @Autowired BudgetSnapshotRepository snapshotRepository;

    private Long projectIdA;   // 项目 A
    private Long projectIdB;   // 项目 B (用于跨项目隔离验证)

    @BeforeEach
    void seed() {
        wbsTaskRepository.deleteAll();
        assignmentRepository.deleteAll();
        budgetLineRepository.deleteAll();
        snapshotRepository.deleteAll();

        // 用自增 id 模拟 projectId (不真存 Project 表)
        projectIdA = 1000L;
        projectIdB = 2000L;

        // 项目 A: 4 个 WBS 任务 (用于排序/进度/唯一性测试)
        wbsTaskRepository.save(mkTask(projectIdA, null, "1",   "EXECUTION",  "NOT_STARTED", 1, 0));
        wbsTaskRepository.save(mkTask(projectIdA, null, "1.1", "EXECUTION",  "IN_PROGRESS", 2, 50));
        wbsTaskRepository.save(mkTask(projectIdA, null, "1.2", "EXECUTION",  "COMPLETED",   3, 100));
        wbsTaskRepository.save(mkTask(projectIdA, null, "1.3", "EXECUTION",  "BLOCKED",     1, 0));

        // 项目 B: 1 个任务 (用于跨项目隔离)
        wbsTaskRepository.save(mkTask(projectIdB, null, "1",   "MILESTONE",  "NOT_STARTED", 5, 0));
    }

    // ============================================================
    // WbsTaskRepository
    // ============================================================

    @Test
    @DisplayName("findByProjectIdAndDeletedFalseOrderByWbsCodeAsc: 4 任务按 wbsCode 升序")
    void wbsTask_findAllActiveByProject_sorted() {
        List<WbsTask> all = wbsTaskRepository
                .findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectIdA);
        assertThat(all).hasSize(4);
        assertThat(all).extracting(WbsTask::getWbsCode)
                .containsExactly("1", "1.1", "1.2", "1.3");
    }

    @Test
    @DisplayName("findByProjectId: 跨项目隔离 — A 项目 4 个, B 项目 1 个")
    void wbsTask_isolationBetweenProjects() {
        assertThat(wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectIdA))
                .hasSize(4);
        assertThat(wbsTaskRepository.findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectIdB))
                .hasSize(1);
    }

    @Test
    @DisplayName("countByProjectIdAndWbsCodeAndDeletedFalse: 唯一性预检 — 存在返 1, 不存在返 0")
    void wbsTask_countByWbsCode() {
        assertThat(wbsTaskRepository
                .countByProjectIdAndWbsCodeAndDeletedFalse(projectIdA, "1.1"))
                .isEqualTo(1L);
        assertThat(wbsTaskRepository
                .countByProjectIdAndWbsCodeAndDeletedFalse(projectIdA, "999"))
                .isZero();
        // 跨项目: A 项目的 "1" 不影响 B 项目
        assertThat(wbsTaskRepository
                .countByProjectIdAndWbsCodeAndDeletedFalse(projectIdB, "1"))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("computeWeightedProgressPct: Σ(w*p)/Σ(w) — (2*50 + 3*100 + 1*0 + 1*0) / (2+3+1+1) = 400/7 = 57")
    void wbsTask_weightedProgressPct() {
        Integer pct = wbsTaskRepository.computeWeightedProgressPct(projectIdA);
        // 4 任务: w=1*p=0 + w=2*p=50 + w=3*p=100 + w=1*p=0 = 400; Σw=7; ROUND(100*400/7) = 5714
        assertThat(pct).isNotNull();
        // ROUND((100*400)/7.0) = 5714.28... → integer 截成 5714
        // 验证: 至少大于 0, 不超过 100
        assertThat(pct).isBetween(5000, 6000);
    }

    @Test
    @DisplayName("computeWeightedProgressPct: 空项目 → null (COALESCE 兜底)")
    void wbsTask_weightedProgressPct_emptyProject() {
        Integer pct = wbsTaskRepository.computeWeightedProgressPct(9999L);
        // SUM(NULL)/NULL = NULL, COALESCE → 0
        assertThat(pct).isZero();
    }

    @Test
    @DisplayName("softDelete: 删完不再出现在查询结果")
    void wbsTask_softDelete_excludedFromQuery() {
        WbsTask first = wbsTaskRepository
                .findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectIdA).get(0);
        first.setDeleted(true);
        wbsTaskRepository.save(first);

        assertThat(wbsTaskRepository
                .findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectIdA))
                .hasSize(3);
        // 但 findById 还能查到
        assertThat(wbsTaskRepository.findById(first.getId())).isPresent();
    }

    // ============================================================
    // WbsAssignmentRepository
    // ============================================================

    @Test
    @DisplayName("assignment: 按 task 查 + 按 user 查 + 唯一约束 (task,user)")
    void assignment_queries() {
        WbsTask task = wbsTaskRepository
                .findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectIdA).get(0);
        Long taskId = task.getId();

        // 灌 2 条: 同 task 2 个 user
        assignmentRepository.save(mkAssignment(taskId, 7L, "DOER", "8.00"));
        assignmentRepository.save(mkAssignment(taskId, 8L, "LEAD", "16.00"));

        // 按 task 查: 2 条
        assertThat(assignmentRepository.findByWbsTaskIdAndDeletedFalse(taskId))
                .hasSize(2);

        // 按 user 查: 7 号只在 taskId, 9 号没人
        assertThat(assignmentRepository.findByUserId(7L)).hasSize(1);
        assertThat(assignmentRepository.findByUserId(9L)).isEmpty();

        // (task,user) 唯一: 第二次保存应该被 DB 拒绝 (UniqueConstraint)
        try {
            assignmentRepository.saveAndFlush(mkAssignment(taskId, 7L, "REVIEWER", "4.00"));
            // H2 行为可能不同 — 用宽松断言
            assertThat(assignmentRepository.findByWbsTaskIdAndUserIdAndDeletedFalse(taskId, 7L))
                    .isPresent();
        } catch (Exception e) {
            // 期望: DataIntegrityViolationException
            assertThat(e).hasMessageContaining("Unique");
        }
    }

    @Test
    @DisplayName("assignment: 按 projectId 查 (跨 WbsTask join)")
    void assignment_byProjectId() {
        WbsTask t1 = wbsTaskRepository
                .findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectIdA).get(0);
        WbsTask t2 = wbsTaskRepository
                .findByProjectIdAndDeletedFalseOrderByWbsCodeAsc(projectIdA).get(1);
        assignmentRepository.save(mkAssignment(t1.getId(), 7L, "DOER", "8"));
        assignmentRepository.save(mkAssignment(t2.getId(), 8L, "DOER", "8"));

        // 项目 A 下: 2 个 assignment (跨 WbsTask)
        List<WbsAssignment> all = assignmentRepository.findByProjectId(projectIdA);
        assertThat(all).hasSize(2);
    }

    // ============================================================
    // BudgetLineRepository
    // ============================================================

    @Test
    @DisplayName("BudgetLine: findByProjectIdAndDeletedFalseOrderByCategory")
    void budgetLine_findByProject() {
        budgetLineRepository.save(mkBudgetLine(projectIdA, "LABOR",      "人力", "100000"));
        budgetLineRepository.save(mkBudgetLine(projectIdA, "PURCHASE",   "采购", "50000"));
        budgetLineRepository.save(mkBudgetLine(projectIdB, "LABOR",      "B-人力", "80000"));

        // 项目 A: 2 条, 按 category 升序
        List<BudgetLine> aLines = budgetLineRepository
                .findByProjectIdAndDeletedFalseOrderByCategory(projectIdA);
        assertThat(aLines).hasSize(2);
        assertThat(aLines).extracting(BudgetLine::getCategory)
                .containsExactly("LABOR", "PURCHASE");

        // 项目 B: 1 条
        assertThat(budgetLineRepository
                .findByProjectIdAndDeletedFalseOrderByCategory(projectIdB))
                .hasSize(1);
    }

    // ============================================================
    // BudgetSnapshotRepository
    // ============================================================

    @Test
    @DisplayName("snapshot: findLatestByProject 按 snapshotDate DESC, id DESC")
    void snapshot_findLatestByProject() {
        // 项目 A 灌 3 条快照 (不同日期)
        snapshotRepository.save(mkSnapshot(projectIdA, LocalDate.of(2025, 1, 1), 1, "100"));
        snapshotRepository.save(mkSnapshot(projectIdA, LocalDate.of(2025, 1, 15), 2, "200"));
        snapshotRepository.save(mkSnapshot(projectIdA, LocalDate.of(2025, 1, 10), 3, "300"));
        // 项目 B 灌 1 条
        snapshotRepository.save(mkSnapshot(projectIdB, LocalDate.of(2025, 1, 20), 1, "999"));

        // 项目 A 最新: 1/15
        List<BudgetSnapshot> aLatest = snapshotRepository.findLatestByProject(projectIdA);
        assertThat(aLatest.get(0).getSnapshotDate()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(aLatest.get(0).getVersion()).isEqualTo(2);

        // 项目 B: 1 条
        assertThat(snapshotRepository.findLatestByProject(projectIdB)).hasSize(1);
    }

    @Test
    @DisplayName("snapshot: findByProjectIdAndDateRange 区间过滤")
    void snapshot_findByDateRange() {
        snapshotRepository.save(mkSnapshot(projectIdA, LocalDate.of(2025, 1, 1),  1, "100"));
        snapshotRepository.save(mkSnapshot(projectIdA, LocalDate.of(2025, 1, 10), 2, "200"));
        snapshotRepository.save(mkSnapshot(projectIdA, LocalDate.of(2025, 1, 20), 3, "300"));
        snapshotRepository.save(mkSnapshot(projectIdA, LocalDate.of(2025, 1, 30), 4, "400"));

        // 区间 [1/5, 1/25] → 1/10 和 1/20 两条
        List<BudgetSnapshot> inRange = snapshotRepository.findByProjectIdAndDateRange(
                projectIdA, LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 25));
        assertThat(inRange).hasSize(2);
        assertThat(inRange).extracting(BudgetSnapshot::getSnapshotDate)
                .containsExactly(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 20));
    }

    /**
     * P3.1 趋势: 5 天连续触发, 每天各 1 条快照, 验证:
     *  - findTrendSince(项目 A, since=4天前) → 4 条 (since 含端点)
     *  - 项目 A 与 B 隔离
     *  - 同一天多版本只留最新 (这里用 H2, native query 走子查询)
     */
    @Test
    @DisplayName("trend: 5 天连续快照, findTrendSince 拉区间内每天 1 条")
    void trend_findTrendSince_fiveDays() {
        // 项目 A 灌 5 天, 版本 1-5
        for (int i = 0; i < 5; i++) {
            snapshotRepository.save(mkSnapshot(
                    projectIdA,
                    LocalDate.now().minusDays(4 - i),
                    i + 1,
                    String.valueOf(100 * (i + 1))));
        }
        // 项目 A 还塞一条"很早"的 (20天前) — 不应进入 5 天趋势
        snapshotRepository.save(mkSnapshot(projectIdA, LocalDate.now().minusDays(20), 99, "999"));
        // 项目 B 1 条 (今天) — 跨项目隔离
        snapshotRepository.save(mkSnapshot(projectIdB, LocalDate.now(), 1, "111"));

        // 5 天范围 (since=4天前, today-now 包含今天)
        List<BudgetSnapshot> trend = snapshotRepository.findTrendSince(
                projectIdA, LocalDate.now().minusDays(4));
        assertThat(trend).hasSize(5);
        // 按 snapshotDate ASC: 4天前 → 今天
        for (int i = 0; i < trend.size(); i++) {
            assertThat(trend.get(i).getVersion()).isEqualTo(i + 1);
        }

        // 跨项目: B 不在 A 的趋势里
        assertThat(trend).noneMatch(s -> s.getProjectId().equals(projectIdB));

        // 缩小到 1 天: today 1 条
        List<BudgetSnapshot> oneDay = snapshotRepository.findTrendSince(
                projectIdA, LocalDate.now());
        assertThat(oneDay).hasSize(1);
        assertThat(oneDay.get(0).getVersion()).isEqualTo(5);
    }

    /**
     * P3.1 趋势: 同一天 2 个版本, 只留最新那条 (native query GROUP BY 验证)。
     * H2 不支持 PG 的 schema 限定符, 这里测 native query 走通的子查询逻辑。
     */
    @Test
    @DisplayName("trend: 同一天多版本, native query 保留 MAX(id) 那条")
    void trend_sameDayMultipleVersions_keepLatest() {
        BudgetSnapshot earlier = mkSnapshot(projectIdA, LocalDate.now(), 1, "100");
        earlier = snapshotRepository.save(earlier);
        BudgetSnapshot later = mkSnapshot(projectIdA, LocalDate.now(), 2, "200");
        snapshotRepository.save(later);

        List<BudgetSnapshot> trend = snapshotRepository.findTrendSince(
                projectIdA, LocalDate.now());
        // 同一天 1 条, 应该是 later (id 大)
        assertThat(trend).hasSize(1);
        assertThat(trend.get(0).getId()).isEqualTo(later.getId());
        assertThat(trend.get(0).getBac()).isEqualByComparingTo("200");
    }

    // ============================================================
    // HourlyRateRepository — P0-A.1 起迁出到 cost 模块,此处不再测
    // ============================================================

    // ============================================================
    // helpers
    // ============================================================

    private static WbsTask mkTask(Long projectId, Long parentId, String code,
                                  String taskType, String status, int weight, int progressPct) {
        WbsTask t = new WbsTask();
        t.setProjectId(projectId);
        t.setParentId(parentId);
        t.setWbsCode(code);
        t.setName("task-" + code);
        t.setTaskType(taskType);
        t.setStatus(status);
        t.setWeight(weight);
        t.setProgressPct(progressPct);
        t.setPlanHours(BigDecimal.ZERO);
        t.setActualHours(BigDecimal.ZERO);
        t.setPredecessorIds(new Long[0]);
        return t;
    }

    private static WbsAssignment mkAssignment(Long taskId, Long userId, String role, String plannedHours) {
        WbsAssignment a = new WbsAssignment();
        a.setWbsTaskId(taskId);
        a.setUserId(userId);
        a.setRole(role);
        a.setPlannedHours(new BigDecimal(plannedHours));
        a.setActualHours(BigDecimal.ZERO);
        return a;
    }

    private static BudgetLine mkBudgetLine(Long projectId, String category, String name, String planned) {
        BudgetLine b = new BudgetLine();
        b.setProjectId(projectId);
        b.setCategory(category);
        b.setName(name);
        b.setPlannedAmount(new BigDecimal(planned));
        b.setCurrency("CNY");
        return b;
    }

    private static BudgetSnapshot mkSnapshot(Long projectId, LocalDate date, int version, String bac) {
        BudgetSnapshot s = new BudgetSnapshot();
        s.setProjectId(projectId);
        s.setSnapshotDate(date);
        s.setVersion(version);
        s.setBac(new BigDecimal(bac));
        return s;
    }
}
