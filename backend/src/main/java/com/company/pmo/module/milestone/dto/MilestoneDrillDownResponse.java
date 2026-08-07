package com.company.pmo.module.milestone.dto;

/**
 * 里程碑分析 - 下钻 (具体里程碑/状态 → 命中项目列表)
 *
 * <p>field 语义:
 * <ul>
 *   <li>phaseId / phaseName — 主视图点的 phase (7 阶段之一, 可能为 null)</li>
 *   <li>milestoneId / milestoneName — 具体里程碑 (二选一, 可能为 null)</li>
 *   <li>statusCode — 4 status 之一 (PENDING/IN_PROGRESS/COMPLETED/DELAYED)</li>
 *   <li>filters — 给前端展示当前下钻路径: "研发部 → 设计 → 方案设计 → 进行中"</li>
 * </ul>
 */
public record MilestoneDrillDownResponse(
        Long phaseId,
        String phaseName,
        Long milestoneId,
        String milestoneName,
        String statusCode,
        String statusName,
        long total,
        String filters,
        java.util.List<ProjectRow> projects
) {
    public record ProjectRow(
            Long projectId,
            String projectCode,
            String projectName,
            Long buId,
            String buName,
            Long pmUserId,
            String pmName,
            Long departmentId,
            String departmentName,
            String planDate,
            String actualDate,
            Integer weight,
            String statusCode,
            String statusName,
            String milestoneName
    ) {}
}
