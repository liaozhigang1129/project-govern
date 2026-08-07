package com.company.pmo.module.cost.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class CostDtos {

    /**
     * 角色档字典条目 (6 档) — GET /api/cost/role-defaults
     */
    public record RoleCostDefaultItem(
            String code,
            String name,
            BigDecimal rate,
            Integer sortOrder
    ) {}

    /**
     * 角色档更新请求 — PUT /api/cost/role-defaults
     */
    public record RoleCostDefaultUpdateRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("code") String code,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") BigDecimal rate
    ) {}

    /**
     * 单条时薪 — GET/POST /api/cost/hourly-rates
     */
    public record HourlyRateItem(
            Long id,
            Long userId,
            String userName,
            String roleCode,
            BigDecimal rate,
            String effectiveMonth,
            String endMonth,
            String remark,
            Long createdBy,
            String createdAt,
            String updatedAt
    ) {}

    /**
     * 时薪 upsert 请求 — POST/PUT /api/cost/hourly-rates
     * effectiveMonth 用 YearMonth 格式 "2026-06"
     */
    public record HourlyRateUpsertRequest(
            @jakarta.validation.constraints.NotNull Long userId,
            @jakarta.validation.constraints.NotBlank String roleCode,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") BigDecimal rate,
            @jakarta.validation.constraints.NotNull java.time.YearMonth effectiveMonth,
            String remark
    ) {}

    /**
     * CSV 导入结果
     */
    public record CsvRowResult(
            int total,
            int success,
            int failed,
            List<String> errors
    ) {}

    // ============================================================
    // F2 多维成本核算 — T3 视图的 HTTP 出口
    // ============================================================

    /**
     * 单条多维行 (项目 / 阶段 / 部门通用)
     *  - 字段统一: key, label, yearMonth, hours, cost, headcount
     *  - 阶段额外带 phaseId/phaseName; 部门额外带 deptCode; 项目额外带 budget
     */
    public record CostDimensionRow(
            String dimension,        // PROJECT / PHASE / DEPT
            String key,               // 主键 (projectId / phaseId+projectId / deptId)
            String code,              // 业务 code
            String label,             // 展示名
            Long phaseId,             // 阶段专用
            String phaseName,         // 阶段专用
            Integer sortOrder,        // 阶段专用
            String yearMonth,         // "2026-06"
            BigDecimal hours,
            BigDecimal cost,
            BigDecimal budget,        // 项目专用
            BigDecimal costRate,      // cost/hours 派生: 人均时薪 (诊断用)
            Long headcount,
            BigDecimal costPct        // 占比 (相对传入 total)
    ) {}

    /**
     * 多维成本汇总响应
     */
    public record CostDimensionResponse(
            String dimension,        // PROJECT / PHASE / DEPT
            String yearMonth,        // "2026-06" 或 "ALL"
            BigDecimal totalHours,
            BigDecimal totalCost,
            Long totalHeadcount,
            Long activeProjects,     // 活跃项目数 (去重)
            BigDecimal avgCostPerUser,    // 人均成本
            BigDecimal budgetCoveragePct, // 预算覆盖率 (有预算的项目 / 总项目)
            BigDecimal avgHourlyRate,     // 平均时薪 (cost/hours)
            List<CostDimensionRow> rows
    ) {}
}
