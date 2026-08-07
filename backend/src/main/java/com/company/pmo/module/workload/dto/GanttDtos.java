package com.company.pmo.module.workload.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 甘特图 API 响应(P1.5 收尾)
 *
 * 一个项目一条 GanttBar: 开始 / 结束 / 计划 / 实际 / 进度 / 里程碑
 * 多个项目聚合 → 前端可以横轴画时间线,纵轴一条条甘特条
 */
public class GanttDtos {

    public record GanttBar(
            Long projectId,
            String projectCode,
            String projectName,
            /** 计划开始(= plan_start_date),可空 */
            LocalDate planStart,
            /** 计划结束 */
            LocalDate planEnd,
            /** 实际开始(可能 null) */
            LocalDate actualStart,
            /** 实际结束(可能 null) */
            LocalDate actualEnd,
            /** 0-100 整数 */
            int progressPct,
            /** 该项目下的里程碑,按 sequence 升序 */
            List<Milestone> milestones
    ) {}

    public record Milestone(
            Long id,
            String name,
            /** 计划完成日 */
            LocalDate planDate,
            /** 实际完成日(null=未完成) */
            LocalDate actualDate,
            String status,        // PENDING/IN_PROGRESS/COMPLETED/DELAYED
            int weight,
            /** 阶段 id (1-7:V3.1 立项/需求/设计/开发/测试/上线运维/维保) */
            Long phaseId,
            /** 阶段名(展示用) */
            String phaseName
    ) {}

    public record GanttResponse(
            /** 区间起点(自动等于所有 planStart/actualStart 的最小值) */
            LocalDate rangeFrom,
            /** 区间终点 */
            LocalDate rangeTo,
            int projectCount,
            List<GanttBar> bars
    ) {}
}