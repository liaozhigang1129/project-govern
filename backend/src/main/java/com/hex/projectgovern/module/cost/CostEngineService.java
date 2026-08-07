package com.hex.projectgovern.module.cost;

import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.module.cost.dto.*;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import com.hex.projectgovern.module.timesheet.TimesheetEntry;
import com.hex.projectgovern.module.timesheet.TimesheetStatus;
import com.hex.projectgovern.module.timesheet.TimesheetWeek;
import com.hex.projectgovern.module.timesheet.TimesheetWeekRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * P0-A.1 成本引擎 — F1 工时→成本核算
 *
 * <p>核心方法:
 * <ul>
 *   <li>{@link #computeUserMonthCost(Long, YearMonth)} — F1 主验收: GET /api/cost/user/{userId}?month=2026-06</li>
 *   <li>{@link #computeUserDayCost(Long, LocalDate)}    — 辅助: 单日成本</li>
 * </ul>
 *
 * <p>算法:
 * <pre>
 *   for each timesheet_entry of user in [month]
 *     rate, source = HourlyRateService.resolveRate(userId, primaryRole.code, workDate)
 *     cost += hours × rate
 *   聚合 → UserMonthCostResponse
 * </pre>
 *
 * <p>复用:
 * <ul>
 *   <li>已有 {@code timesheet_week} / {@code timesheet_entry} (V1.6)</li>
 *   <li>已有 {@code app_user.primaryRole} (V2.9 role_mgmt)</li>
 *   <li>{@link HourlyRateService#resolveRate} 4 级兜底</li>
 * </ul>
 *
 * @since V4.0 (2026-Q2)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostEngineService {

    private final HourlyRateService rateService;
    private final TimesheetWeekRepository timesheetRepo;
    private final UserRepository userRepository;

    // ============================================================
    // F1 主验收 — 月度成本
    // ============================================================

    @Transactional(readOnly = true)
    public UserMonthCostResponse computeUserMonthCost(Long userId, YearMonth month) {
        if (userId == null) throw new BusinessException(400, "userId 必填");
        if (month == null) throw new BusinessException(400, "month 必填 (YYYY-MM)");
        AppUser u = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(404, "user 不存在: " + userId));
        String primaryRole = u.getPrimaryRole() == null ? null : u.getPrimaryRole().getCode();

        LocalDate from = month.atDay(1);
        LocalDate to   = month.atEndOfMonth();
        List<TimesheetWeek> weeks = timesheetRepo.findUserRange(userId, from, to);

        // 聚合
        List<CostBreakdownItem> items = new ArrayList<>();
        BigDecimal totalHours = BigDecimal.ZERO;
        BigDecimal totalCost  = BigDecimal.ZERO;
        BigDecimal userOverride = BigDecimal.ZERO, roleOverride = BigDecimal.ZERO,
                   roleDefault  = BigDecimal.ZERO, userDefault  = BigDecimal.ZERO,
                   none         = BigDecimal.ZERO;

        for (TimesheetWeek w : weeks) {
            // 仅取 APPROVED 周报 (DRAFT/SUBMITTED 不算成本; 与现有 Hours 视图口径一致)
            if (w.getStatus() != TimesheetStatus.APPROVED) continue;
            for (TimesheetEntry e : w.getEntries()) {
                LocalDate d = e.getWorkDate();
                if (d.isBefore(from) || d.isAfter(to)) continue;

                var res = rateService.resolveRate(userId, primaryRole, d);
                BigDecimal hours = e.getHours() == null ? BigDecimal.ZERO : e.getHours();
                BigDecimal cost  = hours.multiply(res.rate()).setScale(2, RoundingMode.HALF_UP);

                // 项目名/编码: 当前不入项目详情表 (性能), 留 projectId 让前端反查
                items.add(new CostBreakdownItem(
                        e.getProjectId(),
                        null,            // projectCode
                        null,            // projectName
                        e.getMilestoneId(),
                        hours,
                        res.rate(),
                        res.source().name(),
                        cost
                ));
                totalHours = totalHours.add(hours);
                totalCost  = totalCost.add(cost);
                // 工时是 NUMERIC(5,2) — 直接累加 BigDecimal 即可, 最终 scale 2
                switch (res.source()) {
                    case USER_OVERRIDE     -> userOverride = userOverride.add(hours);
                    case ROLE_OVERRIDE     -> roleOverride = roleOverride.add(hours);
                    case ROLE_COST_DEFAULT -> roleDefault  = roleDefault.add(hours);
                    case USER_DEFAULT      -> userDefault  = userDefault.add(hours);
                    case NONE              -> none         = none.add(hours);
                }
            }
        }

        UserMonthCostResponse.RateSourceBreakdown breakdown =
                new UserMonthCostResponse.RateSourceBreakdown(
                        userOverride.longValue(), roleOverride.longValue(),
                        roleDefault.longValue(),  userDefault.longValue(),
                        none.longValue());

        log.debug("[Cost] user={} month={} hours={} cost={} breakdown={}",
                userId, month, totalHours, totalCost, breakdown);
        return new UserMonthCostResponse(
                userId, u.getFullName(),
                month.toString(),
                totalHours.setScale(2, RoundingMode.HALF_UP),
                totalCost.setScale(2, RoundingMode.HALF_UP),
                primaryRole,
                items,
                breakdown
        );
    }

    // ============================================================
    // 单日成本 — 验收用例 F1: 张三 6 月 15 日 40h 应为 ¥24,000 (rate=600)
    // ============================================================

    @Transactional(readOnly = true)
    public UserDayCostResponse computeUserDayCost(Long userId, LocalDate date) {
        if (userId == null) throw new BusinessException(400, "userId 必填");
        if (date == null)   throw new BusinessException(400, "date 必填 (YYYY-MM-DD)");
        AppUser u = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(404, "user 不存在: " + userId));
        String primaryRole = u.getPrimaryRole() == null ? null : u.getPrimaryRole().getCode();

        LocalDate weekStart = date.minusDays(date.getDayOfWeek().getValue() - 1);
        LocalDate weekEnd   = weekStart.plusDays(6);
        List<TimesheetWeek> weeks = timesheetRepo.findUserRange(userId, weekStart, weekEnd);

        List<CostBreakdownItem> items = new ArrayList<>();
        BigDecimal hours = BigDecimal.ZERO;
        BigDecimal cost  = BigDecimal.ZERO;
        var res = rateService.resolveRate(userId, primaryRole, date);

        for (TimesheetWeek w : weeks) {
            if (w.getStatus() != TimesheetStatus.APPROVED) continue;
            for (TimesheetEntry e : w.getEntries()) {
                if (!e.getWorkDate().isEqual(date)) continue;
                BigDecimal h = e.getHours() == null ? BigDecimal.ZERO : e.getHours();
                BigDecimal c = h.multiply(res.rate()).setScale(2, RoundingMode.HALF_UP);
                items.add(new CostBreakdownItem(
                        e.getProjectId(), null, null,
                        e.getMilestoneId(), h, res.rate(), res.source().name(), c));
                hours = hours.add(h);
                cost  = cost.add(c);
            }
        }
        return new UserDayCostResponse(
                userId, u.getFullName(), date,
                hours.setScale(2, RoundingMode.HALF_UP),
                cost.setScale(2, RoundingMode.HALF_UP),
                res.rate(),
                res.source().name(),
                primaryRole,
                items
        );
    }
}