package com.company.zhiyu.module.timesheet;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.module.admin.SystemConfigService;
import com.company.zhiyu.module.dict.ProjectStatus;
import com.company.zhiyu.module.dict.ProjectStatusRepository;
import com.company.zhiyu.module.dingtalk.DingTalkAttendanceDaily;
import com.company.zhiyu.module.dingtalk.DingTalkAttendanceDailyRepository;
import com.company.zhiyu.module.dingtalk.DingTalkLeave;
import com.company.zhiyu.module.dingtalk.DingTalkLeaveRepository;
import com.company.zhiyu.module.member.ProjectMember;
import com.company.zhiyu.module.member.ProjectMemberRepository;
import com.company.zhiyu.module.org.AppUser;
import com.company.zhiyu.module.org.Department;
import com.company.zhiyu.module.org.DepartmentService;
import com.company.zhiyu.module.org.UserRepository;
import com.company.zhiyu.module.project.Project;
import com.company.zhiyu.module.project.ProjectRepository;
import com.company.zhiyu.module.timesheet.TimesheetAutoFillDtos.AutoFillRequest;
import com.company.zhiyu.module.timesheet.TimesheetAutoFillDtos.AutoFillResult;
import com.company.zhiyu.module.timesheet.TimesheetAutoFillDtos.BatchAutoFillRequest;
import com.company.zhiyu.module.timesheet.TimesheetAutoFillDtos.BatchAutoFillResult;
import com.company.zhiyu.module.timesheet.TimesheetAutoFillDtos.DayFillResult;
import com.company.zhiyu.module.wbs.WbsAssignment;
import com.company.zhiyu.module.wbs.WbsAssignmentRepository;
import com.company.zhiyu.module.wbs.WbsTask;
import com.company.zhiyu.module.wbs.WbsTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * V4.34 工时自动填报 Service
 *
 * 业务逻辑:
 *   1) 拉用户当周 [weekStart, weekEnd] 的所有 (workDate) 候选
 *   2) 拉考勤(dingtalk_attendance_daily) → 算 workDurationMinutes
 *   3) 拉请假(dingtalk_leave) → 算 leaveHours (规则: <8h→4h, >=8h→8h)
 *   4) hours = max(0, workMinutes/60 - leaveHours)  (整天请假 → hours=0 仍写占位)
 *   5) 候选项目 (project_member 当前在职) + 项目列表(N+1 一次查)
 *   6) 优先级打分:
 *      PM (project.pm_user_id = userId) > BU (project.bu_id) > PL (project.pl_id)
 *      > DEPT_GROUP (project.department_id ∈ user.dept 子树)
 *      > WBS (wbs_assignment 命中) > PLACEHOLDER
 *   7) WBS 任务 → milestone_id (取 IN_PROGRESS 优先, 否则 plan 日期内最早)
 *   8) 写 timesheet_entry (走 TimesheetService.upsertEntries):
 *      - overwrite=false (默认) → 已存在 entry 跳过
 *      - overwrite=true → 覆盖
 *
 * 已存在 entry 判定: (week_id, work_date, project_id, milestone_id) 已存在
 *   → 跳过这一行, 不覆盖人工填的数据
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimesheetAutoFillService {

    /** 半天天数 (4h) / 全天天数 (8h) */
    private static final BigDecimal HALF_DAY_HOURS = new BigDecimal("4");
    private static final BigDecimal FULL_DAY_HOURS = new BigDecimal("8");
    private static final BigDecimal ONE_HOUR = new BigDecimal("1");

    /** 占位项目 code — V4.34 migration 创建 */
    private static final String PLACEHOLDER_CODE = "PLACEHOLDER";

    private final TimesheetService timesheetService;
    private final TimesheetWeekRepository timesheetWeekRepo;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WbsAssignmentRepository wbsAssignmentRepository;
    private final WbsTaskRepository wbsTaskRepository;
    private final DepartmentService departmentService;
    private final ProjectStatusRepository projectStatusRepository;
    private final DingTalkAttendanceDailyRepository attendanceDailyRepository;
    private final DingTalkLeaveRepository leaveRepository;
    private final SystemConfigService systemConfigService;

    // ============================================================
    //  公开入口
    // ============================================================

    /**
     * 单用户单周自动填报
     */
    @Transactional
    public AutoFillResult autoFill(AutoFillRequest req) {
        requireEnabled();
        validateMonday(req.getWeekStart());
        if (req.getUserId() == null) {
            throw new BusinessException(400, "userId 必填");
        }
        AppUser user = userRepository.findByIdAndDeletedFalse(req.getUserId())
                .orElseThrow(() -> new BusinessException(404, "用户不存在: " + req.getUserId()));
        boolean overwrite = Boolean.TRUE.equals(req.getOverwrite());
        boolean dryRun = Boolean.TRUE.equals(req.getDryRun());

        LocalDate weekEnd = req.getWeekStart().plusDays(6);
        return doAutoFill(user, req.getWeekStart(), weekEnd, overwrite, dryRun);
    }

    /**
     * 批量自动填报 (PMO_ADMIN 范围跑)
     */
    @Transactional
    public BatchAutoFillResult autoFillBatch(BatchAutoFillRequest req) {
        requireEnabled();
        validateMonday(req.getWeekStart());
        boolean overwrite = Boolean.TRUE.equals(req.getOverwrite());
        boolean dryRun = Boolean.TRUE.equals(req.getDryRun());
        LocalDate weekEnd = req.getWeekStart().plusDays(6);

        List<AppUser> users = pickTargetUsers(req.getUserIds());
        List<AutoFillResult> results = new ArrayList<>();
        int success = 0, skipped = 0, error = 0;
        for (AppUser u : users) {
            try {
                AutoFillResult r = doAutoFill(u, req.getWeekStart(), weekEnd, overwrite, dryRun);
                results.add(r);
                if (r.getFilledDays() > 0) success++;
                else skipped++;
            } catch (BusinessException e) {
                log.warn("[AutoFill] user={} skip: {}", u.getId(), e.getMessage());
                skipped++;
            } catch (Exception e) {
                log.error("[AutoFill] user={} failed", u.getId(), e);
                error++;
            }
        }
        return BatchAutoFillResult.builder()
                .weekStart(req.getWeekStart())
                .requested(users.size())
                .successCount(success)
                .skippedCount(skipped)
                .errorCount(error)
                .results(results)
                .build();
    }

    // ============================================================
    //  核心算法
    // ============================================================

    private AutoFillResult doAutoFill(AppUser user, LocalDate weekStart, LocalDate weekEnd,
                                      boolean overwrite, boolean dryRun) {
        // 1) 周报 (DRAFT 状态, 拿 timesheet_id 准备 upsertEntries)
        TimesheetWeek week = timesheetWeekRepo
                .findByUserIdAndWeekStartAndDeletedFalse(user.getId(), weekStart)
                .orElseGet(() -> {
                    if (dryRun) {
                        // dryRun 不允许新建周报, 返回虚拟空对象
                        TimesheetWeek t = new TimesheetWeek();
                        t.setUserId(user.getId());
                        t.setWeekStart(weekStart);
                        t.setWeekEnd(weekEnd);
                        t.setStatus(TimesheetStatus.DRAFT);
                        return t;
                    }
                    TimesheetWeek t = new TimesheetWeek();
                    t.setUserId(user.getId());
                    t.setWeekStart(weekStart);
                    t.setWeekEnd(weekEnd);
                    t.setStatus(TimesheetStatus.DRAFT);
                    return timesheetWeekRepo.save(t);
                });
        if (!dryRun && week.getStatus() != TimesheetStatus.DRAFT) {
            throw new BusinessException(409, "周报非 DRAFT 不可自动填充, 当前: " + week.getStatus()
                    + ", weekId=" + week.getId());
        }

        AutoFillContext ctx = buildContext(user, weekStart, weekEnd, week);

        // 3) 占位项目
        Project placeholder = projectRepository.findAllActive().stream()
                .filter(p -> PLACEHOLDER_CODE.equals(p.getCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(500,
                        "占位项目 PLACEHOLDER 不存在, 请先执行 V4.34 迁移"));

        // 4) 遍历 7 天
        List<DayFillResult> dayResults = new ArrayList<>();
        int filledCount = 0, skipCount = 0, placeholderCount = 0;
        double totalHours = 0.0;

        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            DayFillResult r = fillOneDay(ctx, day, placeholder);
            dayResults.add(r);
            if (Boolean.TRUE.equals(r.getSkipped())) {
                skipCount++;
            } else {
                if (r.getProjectId() != null && r.getProjectId().equals(placeholder.getId())) {
                    placeholderCount++;
                } else {
                    filledCount++;
                }
                totalHours += r.getHours() == null ? 0.0 : r.getHours();
            }
        }

        // 5) 写库 (走 TimesheetService.upsertEntries, 避免重复实现)
        if (!dryRun) {
            writeEntries(ctx, dayResults, overwrite);
        }

        return AutoFillResult.builder()
                .userId(user.getId())
                .userName(user.getFullName())
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .dryRun(dryRun)
                .overwrite(overwrite)
                .totalDays(7)
                .filledDays(filledCount)
                .skippedDays(skipCount)
                .placeholderDays(placeholderCount)
                .totalHours(round2(totalHours))
                .days(dayResults)
                .summary(buildSummary(user, weekStart, filledCount, skipCount, placeholderCount, totalHours))
                .build();
    }

    // ============================================================
    //  上下文构建 (一次拉齐, 避免 7 天循环 N+1)
    // ============================================================

    private AutoFillContext buildContext(AppUser user, LocalDate weekStart, LocalDate weekEnd, TimesheetWeek week) {
        ZoneId zone = ZoneId.systemDefault();

        // 1) 考勤: 一次性拉整周
        Map<LocalDate, DingTalkAttendanceDaily> attMap = new HashMap<>();
        for (DingTalkAttendanceDaily d : attendanceDailyRepository.findByPmoUserIdAndRange(
                user.getId(), weekStart, weekEnd)) {
            attMap.put(d.getWorkDate(), d);
        }

        // 2) 请假: 钉钉 userid 维度, 7 天分别查 (避免 union 区间)
        Map<LocalDate, List<DingTalkLeave>> leavesMap = new HashMap<>();
        String dingtalkUid = user.getDingtalkUserId();
        if (dingtalkUid != null && !dingtalkUid.isBlank()) {
            for (int i = 0; i < 7; i++) {
                LocalDate day = weekStart.plusDays(i);
                List<DingTalkLeave> covering = leaveRepository.findCovering(
                        dingtalkUid,
                        day.atStartOfDay(zone).toInstant(),
                        day.plusDays(1).atStartOfDay(zone).toInstant());
                if (!covering.isEmpty()) {
                    leavesMap.put(day, covering);
                }
            }
        }

        // 3) 候选项目: 在职项目成员
        List<ProjectMember> memberships = projectMemberRepository.findActiveByUserAndRange(
                user.getId(), weekStart, weekEnd);

        // 4) 项目批量查 (避免 N+1)
        Set<Long> projectIds = memberships.stream()
                .map(ProjectMember::getProjectId)
                .collect(Collectors.toSet());
        Map<Long, Project> projectMap = new HashMap<>();
        if (!projectIds.isEmpty()) {
            for (Project p : projectRepository.findByIdInAndDeletedFalse(projectIds)) {
                projectMap.put(p.getId(), p);
            }
        }

        // 5) WBS 分配 (按范围)
        List<WbsAssignment> assignments = new ArrayList<>();
        for (ProjectMember m : memberships) {
            assignments.addAll(wbsAssignmentRepository.findByUserId(user.getId()));
        }
        // 去重
        Map<Long, WbsAssignment> assignById = new HashMap<>();
        for (WbsAssignment a : assignments) {
            assignById.put(a.getId(), a);
        }

        // 6) 部门子树 (用于 DEPT_GROUP 规则)
        Set<Long> deptSubtreeIds = new HashSet<>();
        Department ownDept = null;
        if (user.getDepartmentId() != null) {
            try {
                deptSubtreeIds.addAll(departmentService.findDescendantIds(user.getDepartmentId()));
            } catch (Exception e) {
                log.warn("[AutoFill] 部门子树查询失败 user={}, deptId={}: {}",
                        user.getId(), user.getDepartmentId(), e.getMessage());
            }
            try {
                ownDept = departmentService.findDescendants(user.getDepartmentId()).stream()
                        .filter(d -> d.getId().equals(user.getDepartmentId()))
                        .findFirst().orElse(null);
            } catch (Exception ignore) { }
        }

        // 7) 活跃 status id
        Long activeStatusId = projectStatusRepository.findByCode("ACTIVE").map(s -> s.getId()).orElse(null);

        return new AutoFillContext(
                user, weekStart, weekEnd, week,
                attMap, leavesMap, projectMap, memberships,
                new ArrayList<>(assignById.values()),
                deptSubtreeIds, ownDept, activeStatusId, zone);
    }

    // ============================================================
    //  核心 - 一天
    // ============================================================

    private DayFillResult fillOneDay(AutoFillContext ctx, LocalDate day, Project placeholder) {
        // 1) 考勤
        DingTalkAttendanceDaily att = ctx.getAttendanceByDay().get(day);
        Integer workMinutes = att == null ? null : att.getWorkDuration();

        // 2) 请假
        double leaveHours = sumLeaveHours(ctx, day);

        // 3) hours
        double baseHours = workMinutes == null ? 0.0 : workMinutes / 60.0;
        double finalHours = Math.max(0.0, round2(baseHours - leaveHours));
        if (finalHours > 24.0) finalHours = 24.0;  // 防御

        // 4) 候选项目
        ProjectMatch match = pickBestProject(ctx, day);

        Long projectId;
        Long milestoneId;
        AutoFillMatchReason reason;
        String description;

        if (match != null) {
            projectId = match.project().getId();
            milestoneId = match.milestoneId();
            reason = match.reason();
            description = reason.description() + " — " + match.project().getName();
        } else {
            projectId = placeholder.getId();
            milestoneId = null;
            reason = AutoFillMatchReason.PLACEHOLDER;
            description = reason.description() + " — " + (att == null ? "无打卡" : "无项目候选");
        }

        // 5) 跳过已写过的
        boolean skip = ctx.isAlreadyWritten(projectId, day, milestoneId);
        if (skip) {
            return DayFillResult.builder()
                    .workDate(day)
                    .workDurationMinutes(workMinutes)
                    .leaveHours(leaveHours)
                    .matchReason(reason.name())
                    .projectId(projectId)
                    .milestoneId(milestoneId)
                    .priority(reason.getPriority())
                    .hours(0.0)
                    .description(description + " (跳过: 同日同项目已存在)")
                    .skipped(true)
                    .build();
        }
        ctx.markWritten(projectId, day, milestoneId);

        return DayFillResult.builder()
                .workDate(day)
                .workDurationMinutes(workMinutes)
                .leaveHours(leaveHours)
                .matchReason(reason.name())
                .projectId(projectId)
                .milestoneId(milestoneId)
                .priority(reason.getPriority())
                .hours(finalHours)
                .description(description)
                .skipped(false)
                .build();
    }

    // ============================================================
    //  请假小时换算 (规则 3)
    // ============================================================

    private double sumLeaveHours(AutoFillContext ctx, LocalDate day) {
        List<DingTalkLeave> leaves = ctx.getLeavesByDay().get(day);
        if (leaves == null || leaves.isEmpty()) return 0.0;
        double totalCovered = 0.0;
        for (DingTalkLeave l : leaves) {
            double hours = hoursOfLeave(l, day, ctx.getZone());
            totalCovered += hours;
        }
        if (totalCovered > 8.0) totalCovered = 8.0;
        if (totalCovered >= 8.0) return 8.0;
        if (totalCovered > 0.0) return 4.0;
        return 0.0;
    }

    private double hoursOfLeave(DingTalkLeave l, LocalDate day, ZoneId zone) {
        BigDecimal dur = l.getDuration() == null ? BigDecimal.ZERO : l.getDuration();
        double raw;
        if ("DAY".equalsIgnoreCase(l.getDurationUnit())) {
            raw = dur.doubleValue() * 8.0;
        } else {
            raw = dur.doubleValue();
        }
        Instant dayStart = day.atStartOfDay(zone).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
        Instant start = l.getStartTime() == null ? dayStart : l.getStartTime();
        Instant end = l.getEndTime() == null ? dayEnd : l.getEndTime();
        if (start.isBefore(dayStart)) start = dayStart;
        if (end.isAfter(dayEnd)) end = dayEnd;
        if (!end.isAfter(start)) return 0.0;
        double covered = (end.toEpochMilli() - start.toEpochMilli()) / 3_600_000.0;
        return Math.min(raw, covered);
    }

    // ============================================================
    //  候选项目打分
    // ============================================================

    private ProjectMatch pickBestProject(AutoFillContext ctx, LocalDate day) {
        Set<Long> candidateProjectIds = ctx.getActiveMemberships().stream()
                .map(ProjectMember::getProjectId)
                .collect(Collectors.toSet());

        ProjectMatch best = null;

        // 规则 1: PM
        if (ctx.getUser().getId() != null) {
            for (Long pid : candidateProjectIds) {
                Project p = ctx.getProjectsById().get(pid);
                if (p == null) continue;
                if (!isActive(p, ctx.getActiveStatusId())) continue;
                if (ctx.getUser().getId().equals(p.getPmUserId())) {
                    best = chooseBetter(best, new ProjectMatch(p, null, AutoFillMatchReason.PM));
                }
            }
        }
        if (best != null) return best;

        // 规则 4: DEPT_GROUP (BU/PL 暂跳, 字段缺)
        for (Long pid : candidateProjectIds) {
            Project p = ctx.getProjectsById().get(pid);
            if (p == null) continue;
            if (!isActive(p, ctx.getActiveStatusId())) continue;
            if (p.getDepartmentId() != null
                    && ctx.getDepartmentSubtreeIds().contains(p.getDepartmentId())) {
                best = chooseBetter(best, new ProjectMatch(p, null, AutoFillMatchReason.DEPT_GROUP));
            }
        }
        if (best != null) return best;

        // 规则 5: WBS
        for (WbsAssignment a : ctx.getWbsAssignments()) {
            if (!isWithinDay(a, day)) continue;
            WbsTask t = wbsTaskRepository.findById(a.getWbsTaskId()).orElse(null);
            if (t == null || t.isDeleted()) continue;
            Project p = ctx.getProjectsById().get(t.getProjectId());
            if (p == null) continue;
            if (!isActive(p, ctx.getActiveStatusId())) continue;
            best = chooseBetter(best, new ProjectMatch(p, t.getMilestoneId(), AutoFillMatchReason.WBS));
        }
        return best;
    }

    private ProjectMatch chooseBetter(ProjectMatch cur, ProjectMatch candidate) {
        if (cur == null) return candidate;
        if (candidate.reason().getPriority() < cur.reason().getPriority()) return candidate;
        if (candidate.reason().getPriority() > cur.reason().getPriority()) return cur;
        return candidate.project().getId() < cur.project().getId() ? candidate : cur;
    }

    private boolean isActive(Project p, Long activeStatusId) {
        if (activeStatusId == null) return true;
        return p.getStatus() != null && activeStatusId.equals(p.getStatus().getId());
    }

    private boolean isWithinDay(WbsAssignment a, LocalDate day) {
        if (a.getStartDate() != null && a.getStartDate().isAfter(day)) return false;
        if (a.getEndDate() != null && a.getEndDate().isBefore(day)) return false;
        return true;
    }

    private record ProjectMatch(Project project, Long milestoneId, AutoFillMatchReason reason) {}

    // ============================================================
    //  写库
    // ============================================================

    private void writeEntries(AutoFillContext ctx, List<DayFillResult> dayResults, boolean overwrite) {
        Set<String> existingKeys = new HashSet<>();
        for (TimesheetEntry e : ctx.getWeek().getEntries()) {
            existingKeys.add(keyOf(e.getProjectId(), e.getWorkDate(), e.getMilestoneId()));
        }

        List<com.company.zhiyu.module.timesheet.dto.TimesheetDtos.EntryRequest> toWrite = new ArrayList<>();
        for (DayFillResult r : dayResults) {
            if (Boolean.TRUE.equals(r.getSkipped())) continue;
            String k = keyOf(r.getProjectId(), r.getWorkDate(), r.getMilestoneId());
            if (existingKeys.contains(k) && !overwrite) {
                continue;
            }
            com.company.zhiyu.module.timesheet.dto.TimesheetDtos.EntryRequest er =
                    new com.company.zhiyu.module.timesheet.dto.TimesheetDtos.EntryRequest();
            er.setWorkDate(r.getWorkDate());
            er.setProjectId(r.getProjectId());
            er.setMilestoneId(r.getMilestoneId());
            er.setHours(BigDecimal.valueOf(r.getHours() == null ? 0.0 : r.getHours())
                    .setScale(2, RoundingMode.HALF_UP));
            er.setDescription(r.getDescription());
            toWrite.add(er);
            existingKeys.add(k);
        }
        if (toWrite.isEmpty()) return;
        com.company.zhiyu.module.timesheet.dto.TimesheetDtos.EntriesRequest req =
                new com.company.zhiyu.module.timesheet.dto.TimesheetDtos.EntriesRequest();
        req.setEntries(toWrite);
        timesheetService.upsertEntries(ctx.getWeek().getId(), req);
    }

    private static String keyOf(Long projectId, LocalDate day, Long milestoneId) {
        return projectId + "|" + day + "|" + (milestoneId == null ? "NULL" : milestoneId);
    }

    // ============================================================
    //  工具
    // ============================================================

    private void requireEnabled() {
        if (!systemConfigService.getBoolean("timesheet.auto_fill.enabled", true)) {
            throw new BusinessException(423, "工时自动填报功能已被 system_config 关闭");
        }
    }

    private void validateMonday(LocalDate d) {
        if (d == null || d.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BusinessException(400, "weekStart 必须是周一, 实得: "
                    + (d == null ? "null" : d.getDayOfWeek()));
        }
    }

    private double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String buildSummary(AppUser u, LocalDate weekStart, int filled, int skip, int placeholder, double total) {
        return String.format("用户 %s (id=%d) 周 %s: 填充 %d 天 (其中占位 %d 天), 跳过 %d 天, 合计 %.2fh",
                u.getFullName(), u.getId(), weekStart, filled, placeholder, skip, total);
    }

    private List<AppUser> pickTargetUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return userRepository.findAll().stream()
                    .filter(u -> !u.isDeleted() && u.isEnabled())
                    .toList();
        }
        return userRepository.findAllById(userIds).stream()
                .filter(u -> !u.isDeleted() && u.isEnabled())
                .toList();
    }
}
