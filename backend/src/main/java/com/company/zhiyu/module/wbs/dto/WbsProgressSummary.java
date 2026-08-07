package com.company.zhiyu.module.wbs.dto;

import java.math.BigDecimal;

/**
 * 项目级 WBS 进度汇总(给仪表盘/项目头部卡片用)。
 * <p>对齐 {@code v_wbs_progress_summary} 视图(逻辑等价, Service 层用 JPQL 算以避免连数据库视图)。
 */
public record WbsProgressSummary(
        Long projectId,
        long taskCount,
        long completedCount,
        long inProgressCount,
        long blockedCount,
        long notStartedCount,
        long criticalCount,
        long milestoneCount,
        BigDecimal weightedProgressPct,
        BigDecimal totalPlanHours,
        BigDecimal totalActualHours,
        BigDecimal hoursBurnPct
) {}
