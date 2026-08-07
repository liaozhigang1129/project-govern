package com.company.pmo.module.timesheet;

import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.dingtalk.DingTalkAttendanceDaily;
import com.company.pmo.module.dingtalk.DingTalkLeave;
import com.company.pmo.module.member.ProjectMember;
import com.company.pmo.module.org.AppUser;
import com.company.pmo.module.org.Department;
import com.company.pmo.module.project.Project;
import com.company.pmo.module.wbs.WbsAssignment;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V4.34 工时自动填报 - 一次性预加载上下文
 *
 * 避免 7 天循环中重复查 DB:
 *  - 7 天的考勤 (attendance map)
 *  - 7 天的请假 (leave map)
 *  - 用户在职项目 (ProjectMember list)
 *  - 候选项目 (Project map by id)
 *  - WBS 任务分配
 *  - 用户部门及其所有子部门 id 集合
 *  - 活跃 status (ACTIVE)
 */
@Data
@AllArgsConstructor
public class AutoFillContext {

    private final AppUser user;
    private final LocalDate weekStart;
    private final LocalDate weekEnd;
    private final TimesheetWeek week;

    /** key=LocalDate -> attendance */
    private final Map<LocalDate, DingTalkAttendanceDaily> attendanceByDay;
    /** key=LocalDate -> 当天覆盖的所有 leave (可能有多个, 按天合并) */
    private final Map<LocalDate, List<DingTalkLeave>> leavesByDay;
    /** 候选项目 id → Project */
    private final Map<Long, Project> projectsById;
    /** 用户在职项目成员 (projectId list) */
    private final List<ProjectMember> activeMemberships;
    /** WBS 分配 (含 task.projectId + assignment.startDate/endDate) — 不预加载, fillOneDay 即时查 */
    private final List<WbsAssignment> wbsAssignments;
    /** 用户部门及其所有子部门 id 集合 (含自身) */
    private final Set<Long> departmentSubtreeIds;
    /** 用户直属部门 (用于 PL 判定) */
    private final Department ownDepartment;
    /** ProjectStatus 缓存 code -> id (用于 PM/BU 规则判 status=ACTIVE) */
    private final Long activeStatusId;

    /** zone 缓存,转换 Instant <-> LocalDate */
    private final ZoneId zone;

    /** 累计已写入的 entry key (weekId+date+projectId+milestoneId) → 防止同一 (date,project,milestone) 重复写 */
    private final Set<String> writtenKeys = new HashSet<>();

    public boolean isAlreadyWritten(Long projectId, LocalDate day, Long milestoneId) {
        return writtenKeys.contains(keyOf(projectId, day, milestoneId));
    }

    public void markWritten(Long projectId, LocalDate day, Long milestoneId) {
        writtenKeys.add(keyOf(projectId, day, milestoneId));
    }

    private static String keyOf(Long projectId, LocalDate day, Long milestoneId) {
        return projectId + "|" + day + "|" + (milestoneId == null ? "NULL" : milestoneId);
    }

    /** day 当天 00:00:00 (系统时区) */
    public Instant dayStartInstant(LocalDate day) {
        return day.atStartOfDay(zone).toInstant();
    }

    /** day 次日 00:00:00 (系统时区) */
    public Instant dayEndInstant(LocalDate day) {
        return day.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
