package com.company.pmo.module.risk.dto;

import java.util.List;
import java.util.Map;

/**
 * 项目风险健康度聚合 (P4 KPI)。
 * <p>对应 v_risk_health 视图字段, 一次返回给前端看板用。
 *
 * @param projectId       项目 id
 * @param totalCount      总风险数 (含已关闭)
 * @param activeCount     活跃数 (非 CLOSED/ACCEPTED)
 * @param criticalActive  CRITICAL 且活跃
 * @param highActive      HIGH 且活跃
 * @param occurredCount   已发生 (OCCURRED 状态)
 * @param maxActiveScore  活跃风险的最高 score (0 = 无活跃风险)
 * @param byCategory      按 category 分组的活跃数 (e.g. {"TECHNICAL":3, "SCHEDULE":1})
 * @param byLevel         按 level 分组的活跃数 (e.g. {"LOW":1, "HIGH":2})
 */
public record RiskHealthSummary(
        Long projectId,
        long totalCount,
        long activeCount,
        long criticalActive,
        long highActive,
        long occurredCount,
        int  maxActiveScore,
        Map<String, Long> byCategory,
        Map<String, Long> byLevel
) {}
