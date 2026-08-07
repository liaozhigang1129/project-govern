package com.company.zhiyu.module.milestone.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 里程碑分析主视图 (V3.1:按 PHASE 桶 = 7 阶段)
 *
 * <p>结构:
 * <ul>
 *   <li>periodLabel — 给前端展示 "2026-06-01 ~ 2026-06-30"</li>
 *   <li>byPhase — 7 个固定桶 (立项/需求/设计/开发/测试/上线运维/维保), 按 sortOrder 排序</li>
 *   <li>phases — 每桶详情: count + 4 个 status 计数 + 里程碑名明细</li>
 *   <li>totalMilestones — 窗口内总里程碑数</li>
 * </ul>
 */
public record MilestoneAnalysisResponse(
        String scope,
        String periodLabel,
        LocalDate from,
        LocalDate to,
        long totalMilestones,
        List<PhaseBucketItem> byPhase,                // 7 桶卡片数据
        Map<Long, PhaseBucket> phases                 // phaseId -> 详情
) {
    /** 7 阶段桶卡片: phaseId + code + 名称 + count */
    public record PhaseBucketItem(Long phaseId, String code, String phaseName, long count) {}

    /** 单 phase 详情 */
    public record PhaseBucket(
            long count,                          // 该 phase 里程碑总数
            Map<String, Long> byStatus,          // 该 phase 内 4 status 计数
            List<NameStatusCount> byName         // 该 phase 内 name → (count, statusCode) 明细
    ) {}

    /** name 命中:同名 milestone 跨项目可能不同 status, 这里聚合同名同 status */
    public record NameStatusCount(String name, long count, String statusCode) {}
}
