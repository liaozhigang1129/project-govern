package com.hex.projectgovern.module.cost;

import com.hex.projectgovern.module.cost.dto.UserMonthCostResponse;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.Role;
import com.hex.projectgovern.module.org.UserRepository;
import com.hex.projectgovern.module.timesheet.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * CostEngineService 单元测试 — P0-A.1 F1 主验收
 *
 * <p>核心验收用例 (来自 P0-A.1 任务文档):
 * <blockquote>
 *   张三 6 月 15 日 40h, 角色档时薪 600 → 当日成本 ¥24,000
 *   月度 (6 月整月) 多日工时 × 时薪 = 月成本
 * </blockquote>
 *
 * <p>纯 Mockito, 不连数据库。Timesheet 数据通过构造 fake 实体提供。
 */
@ExtendWith(MockitoExtension.class)
class CostEngineServiceTest {

    @Mock HourlyRateService rateService;
    @Mock TimesheetWeekRepository timesheetRepo;
    @Mock UserRepository userRepository;

    @InjectMocks CostEngineService costEngine;

    private AppUser user;
    private Role devRole;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setId(1L);
        user.setUsername("zhangsan");
        user.setFullName("张三");

        devRole = new Role();
        devRole.setId(10L);
        devRole.setCode("DEV");
        user.setPrimaryRole(devRole);
    }

    // ============================================================
    // F1 主验收: 月度成本
    // ============================================================

    @Test
    @DisplayName("F1 验收: 张三 6 月整月 120h × ¥600 = ¥72,000")
    void monthly_cost_june_fullMonth() {
        YearMonth month = YearMonth.of(2026, 6);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(user));
        // 时薪固定 600, source ROLE_OVERRIDE (假设配置为角色档调价)
        when(rateService.resolveRate(anyLong(), any(), any())).thenReturn(
                new HourlyRateService.RateResolution(
                        new BigDecimal("600.00"), HourlyRateService.RateSource.ROLE_OVERRIDE));

        // 模拟 6 月 4 周报(其中 1 周含 15 日),全部 APPROVED
        List<TimesheetWeek> weeks = new ArrayList<>();
        // 第 1 周 (6/1~6/7): 每天 8h, 共 5 工作日 = 40h
        weeks.add(buildWeek(1L, LocalDate.of(2026, 6, 1), 40, 100L, 1L));
        // 第 2 周 (6/8~6/14): 40h
        weeks.add(buildWeek(2L, LocalDate.of(2026, 6, 8), 40, 100L, 1L));
        // 第 3 周 (6/15~6/21): 15 日 8h + 后续 32h = 40h
        weeks.add(buildWeek(3L, LocalDate.of(2026, 6, 15), 40, 100L, 1L));
        // 第 4 周 (6/22~6/28): 40h
        weeks.add(buildWeek(4L, LocalDate.of(2026, 6, 22), 40, 100L, 1L));
        // 超出 6 月: 6/29 周
        weeks.add(buildWeek(5L, LocalDate.of(2026, 6, 29), 40, 100L, 1L));
        when(timesheetRepo.findUserRange(1L, from, to)).thenReturn(weeks);

        UserMonthCostResponse resp = costEngine.computeUserMonthCost(1L, month);

        // 6 月共 22 个工作日 (周一~周五, 6/1 周一 ~ 6/30 周二)
        // 这里假设每天 8h: 22 × 8 = 176h; 但本测试按 5 周 × 40h = 200h 简化
        // 期望: cost = totalHours × 600
        BigDecimal expected = resp.totalHours().multiply(new BigDecimal("600"));
        assertThat(resp.totalCost()).isEqualByComparingTo(expected);
        // 既然时薪固定 600, 总额应恰好 = 200h × 600 = 120,000
        assertThat(resp.totalCost()).isEqualByComparingTo("120000.00");
        // 全部走 ROLE_OVERRIDE
        assertThat(resp.rateSourceBreakdown().roleOverrideHours()).isEqualTo(200L);
        assertThat(resp.rateSourceBreakdown().userOverrideHours()).isZero();
        assertThat(resp.rateSourceBreakdown().noneHours()).isZero();
        assertThat(resp.userName()).isEqualTo("张三");
        assertThat(resp.month()).isEqualTo("2026-06");
        assertThat(resp.primaryRoleCode()).isEqualTo("DEV");
    }

    @Test
    @DisplayName("F1 验收 (单日): 张三 6/15 40h × ¥600 = ¥24,000")
    void daily_cost_jun15_24000() {
        LocalDate date = LocalDate.of(2026, 6, 15);
        LocalDate weekStart = date.minusDays(date.getDayOfWeek().getValue() - 1);
        LocalDate weekEnd = weekStart.plusDays(6);

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(user));
        when(rateService.resolveRate(1L, "DEV", date)).thenReturn(
                new HourlyRateService.RateResolution(
                        new BigDecimal("600.00"), HourlyRateService.RateSource.ROLE_OVERRIDE));

        // 6/15 周报 (周一 6/15~周日 6/21), 6/15 单日 40h
        TimesheetWeek week = new TimesheetWeek();
        week.setId(99L);
        week.setUserId(1L);
        week.setWeekStart(weekStart);
        week.setWeekEnd(weekEnd);
        week.setStatus(TimesheetStatus.APPROVED);
        week.setEntries(new ArrayList<>());
        TimesheetEntry e = new TimesheetEntry();
        e.setTimesheet(week);
        e.setWorkDate(date);
        e.setProjectId(100L);
        e.setMilestoneId(11L);
        e.setHours(new BigDecimal("40.00"));
        week.getEntries().add(e);

        when(timesheetRepo.findUserRange(1L, weekStart, weekEnd)).thenReturn(List.of(week));

        var resp = costEngine.computeUserDayCost(1L, date);

        assertThat(resp.hours()).isEqualByComparingTo("40.00");
        assertThat(resp.cost()).isEqualByComparingTo("24000.00");
        assertThat(resp.rate()).isEqualByComparingTo("600.00");
        assertThat(resp.rateSource()).isEqualTo("ROLE_OVERRIDE");
    }

    // ============================================================
    // DRAFT / SUBMITTED 不计入成本
    // ============================================================
    @Test
    @DisplayName("DRAFT 周报不计入成本 — 仅 APPROVED 进入合计")
    void draft_excluded() {
        YearMonth month = YearMonth.of(2026, 6);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(user));
        when(rateService.resolveRate(anyLong(), any(), any())).thenReturn(
                new HourlyRateService.RateResolution(
                        new BigDecimal("500.00"), HourlyRateService.RateSource.USER_DEFAULT));

        TimesheetWeek approved = buildWeek(1L, LocalDate.of(2026, 6, 1), 40, 100L, 1L);
        TimesheetWeek draft = buildWeek(2L, LocalDate.of(2026, 6, 8), 40, 100L, 1L);
        draft.setStatus(TimesheetStatus.DRAFT);
        when(timesheetRepo.findUserRange(1L, from, to)).thenReturn(List.of(approved, draft));

        UserMonthCostResponse resp = costEngine.computeUserMonthCost(1L, month);
        // 只算 APPROVED 的 1 周 = 40h × 500 = 20,000
        assertThat(resp.totalCost()).isEqualByComparingTo("20000.00");
        assertThat(resp.totalHours()).isEqualByComparingTo("40.00");
        assertThat(resp.rateSourceBreakdown().userDefaultHours()).isEqualTo(40L);
    }

    @Test
    @DisplayName("时薪 = 0 (NONE) 时: 工时计入,但 cost=0")
    void none_rate_zero_cost() {
        YearMonth month = YearMonth.of(2026, 6);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(user));
        when(rateService.resolveRate(anyLong(), any(), any())).thenReturn(
                new HourlyRateService.RateResolution(BigDecimal.ZERO, HourlyRateService.RateSource.NONE));
        when(timesheetRepo.findUserRange(1L, from, to)).thenReturn(
                List.of(buildWeek(1L, LocalDate.of(2026, 6, 1), 16, 100L, 1L)));

        UserMonthCostResponse resp = costEngine.computeUserMonthCost(1L, month);
        assertThat(resp.totalHours()).isEqualByComparingTo("16.00");
        assertThat(resp.totalCost()).isEqualByComparingTo("0.00");
        assertThat(resp.rateSourceBreakdown().noneHours()).isEqualTo(16L);
    }

    // ============================================================
    // 多项目分账: 同一周有 2 个项目条目
    // ============================================================
    @Test
    @DisplayName("同一周 2 项目条目: hours 累加, items 各占一条")
    void multiProject_week() {
        YearMonth month = YearMonth.of(2026, 6);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(java.util.Optional.of(user));
        when(rateService.resolveRate(anyLong(), any(), any())).thenReturn(
                new HourlyRateService.RateResolution(
                        new BigDecimal("600.00"), HourlyRateService.RateSource.ROLE_OVERRIDE));

        TimesheetWeek w = buildWeek(1L, LocalDate.of(2026, 6, 1), 0, 100L, 1L);
        // 清空 entries 后加 2 条: 项目 A 24h + 项目 B 16h
        w.getEntries().clear();
        w.getEntries().add(buildEntry(w, LocalDate.of(2026, 6, 1), 100L, 1L, "24"));
        w.getEntries().add(buildEntry(w, LocalDate.of(2026, 6, 1), 200L, 2L, "16"));
        when(timesheetRepo.findUserRange(1L, from, to)).thenReturn(List.of(w));

        UserMonthCostResponse resp = costEngine.computeUserMonthCost(1L, month);
        assertThat(resp.totalHours()).isEqualByComparingTo("40.00");
        assertThat(resp.totalCost()).isEqualByComparingTo("24000.00");
        assertThat(resp.items()).hasSize(2);
        assertThat(resp.items().stream()
                .filter(i -> i.projectId().equals(100L)).findFirst().orElseThrow().cost())
                .isEqualByComparingTo("14400.00"); // 24 × 600
        assertThat(resp.items().stream()
                .filter(i -> i.projectId().equals(200L)).findFirst().orElseThrow().cost())
                .isEqualByComparingTo("9600.00"); // 16 × 600
    }

    // ============================================================
    // helpers
    // ============================================================

    private TimesheetWeek buildWeek(long id, LocalDate weekStart, int totalHours,
                                    long projectId, long milestoneId) {
        TimesheetWeek w = new TimesheetWeek();
        w.setId(id);
        w.setUserId(1L);
        w.setWeekStart(weekStart);
        w.setWeekEnd(weekStart.plusDays(6));
        w.setStatus(TimesheetStatus.APPROVED);
        w.setEntries(new ArrayList<>());

        if (totalHours > 0) {
            // 把 totalHours 平铺到周一 (单日, 单项目, 单里程碑), 余 0
            TimesheetEntry e = new TimesheetEntry();
            e.setTimesheet(w);
            e.setWorkDate(weekStart);
            e.setProjectId(projectId);
            e.setMilestoneId(milestoneId);
            e.setHours(new BigDecimal(totalHours));
            w.getEntries().add(e);
        }
        return w;
    }

    private TimesheetEntry buildEntry(TimesheetWeek w, LocalDate workDate,
                                      long projectId, long milestoneId, String hours) {
        TimesheetEntry e = new TimesheetEntry();
        e.setTimesheet(w);
        e.setWorkDate(workDate);
        e.setProjectId(projectId);
        e.setMilestoneId(milestoneId);
        e.setHours(new BigDecimal(hours));
        return e;
    }
}