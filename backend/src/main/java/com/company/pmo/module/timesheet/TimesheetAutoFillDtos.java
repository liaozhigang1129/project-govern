package com.company.pmo.module.timesheet;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * V4.34 工时自动填报 — 请求与响应 DTO 集合
 */
public class TimesheetAutoFillDtos {

    /**
     * 自动填报请求
     *  - userId     必填: 目标用户
     *  - weekStart  必填: 周一日期
     *  - dryRun     可选: true = 只返回结果, 不写库 (默认 false = 写)
     *  - overwrite  可选: true = 覆盖该周已存在的 entry (默认 false = 跳过)
     */
    @Data
    @Builder
    public static class AutoFillRequest {
        private Long userId;
        private LocalDate weekStart;
        private Boolean dryRun;
        private Boolean overwrite;
    }

    /**
     * 一天填充结果 (用于 dryRun / 弹窗详情)
     */
    @Data
    @Builder
    public static class DayFillResult {
        private LocalDate workDate;
        /** 当日考勤 (分钟), null = 当天没打卡 */
        private Integer workDurationMinutes;
        /** 当日请假小时 (按规则 3 计算), 0 = 无请假 */
        private Double leaveHours;
        /** 命中规则 (PM / BU / PL / DEPT_GROUP / WBS / PLACEHOLDER) */
        private String matchReason;
        /** 候选项目 ID (实际写入的) */
        private Long projectId;
        /** 候选 WBS 任务 ID (= timesheet_entry.milestone_id) */
        private Long milestoneId;
        /** 命中规则的优先级 (1=PM, 2=BU, 3=PL, 4=DEPT_GROUP, 5=WBS, 6=PLACEHOLDER) */
        private Integer priority;
        /** 写入 hours (已扣请假) */
        private Double hours;
        /** 描述 (含规则名 + 提示) */
        private String description;
        /** 是否跳过 (overwrite=false 且 entry 已存在) */
        private Boolean skipped;
    }

    /**
     * 自动填报结果
     */
    @Data
    @Builder
    public static class AutoFillResult {
        private Long userId;
        private String userName;
        private LocalDate weekStart;
        private LocalDate weekEnd;
        private boolean dryRun;
        private boolean overwrite;
        private int totalDays;
        private int filledDays;
        private int skippedDays;
        private int placeholderDays;
        private double totalHours;
        private List<DayFillResult> days;
        private String summary;
    }

    /**
     * 批量自动填报请求 (PMO_ADMIN 范围跑)
     */
    @Data
    @Builder
    public static class BatchAutoFillRequest {
        private LocalDate weekStart;
        /** 可选: 限定 userId 集合, null/空 = 全员 (实际只跑 enabled=true + deleted=false) */
        private List<Long> userIds;
        private Boolean dryRun;
        private Boolean overwrite;
    }

    /**
     * 批量结果
     */
    @Data
    @Builder
    public static class BatchAutoFillResult {
        private LocalDate weekStart;
        private int requested;
        private int successCount;
        private int skippedCount;
        private int errorCount;
        private List<AutoFillResult> results;
    }
}
