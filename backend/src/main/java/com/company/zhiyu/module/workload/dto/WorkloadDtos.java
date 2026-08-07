package com.company.zhiyu.module.workload.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class WorkloadDtos {

    /** 单人一周的负载(可横向拼成 4 周/8 周矩阵) */
    public record UserWeekRow(
            Long userId,
            String username,
            String fullName,
            Long departmentId,
            String departmentName,
            LocalDate weekStart,
            LocalDate weekEnd,
            String status,            // NO_DATA / DRAFT / SUBMITTED / APPROVED
            BigDecimal totalHours,
            int projectCount,
            int entryCount,
            int milestoneCount,      // P2.5: 该人该周所参与项目下, 窗口内里程碑总数
            int upcomingCount        // P2.5: 其中 14 天内到期的里程碑数
    ) {}

    /** 矩阵响应:人员 × 周 */
    public record UserLoadMatrix(
            List<UserWeekRow> rows,
            LocalDate from,
            LocalDate to,
            int weekCount
    ) {}

    /** 单个项目的人时汇总(可作甘特图数据) */
    public record ProjectMemberHours(
            Long userId,
            String username,
            String fullName,
            BigDecimal totalHours,
            int dayCount
    ) {}

    /** 单个项目的日级工时(可拼成甘特条) */
    public record ProjectDayHours(
            LocalDate workDate,
            BigDecimal totalHours,
            int memberCount
    ) {}

    /** 单项目工时汇总 */
    public record ProjectLoad(
            Long projectId,
            String projectName,
            BigDecimal totalHours,
            int memberCount,
            int dayCount,
            List<ProjectMemberHours> byMember,
            List<ProjectDayHours> byDay
    ) {}

    /** P2.5: 人员 × 周 的里程碑列表(单元格点击下钻用) */
    public record UserMilestoneRow(
            Long milestoneId,
            String milestoneName,
            Long projectId,
            String projectCode,
            String projectName,
            Long phaseId,
            String phaseName,
            String statusCode,       // PENDING/IN_PROGRESS/COMPLETED/DELAYED
            String statusName,
            LocalDate planDate,
            LocalDate actualDate,
            int weight
    ) {}

    public record UserMilestoneList(
            Long userId,
            String fullName,
            LocalDate weekStart,
            LocalDate weekEnd,
            LocalDate from,
            LocalDate to,
            int total,
            List<UserMilestoneRow> items
    ) {}
}
