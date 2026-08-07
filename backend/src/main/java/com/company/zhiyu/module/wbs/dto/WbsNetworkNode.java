package com.company.zhiyu.module.wbs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * P3.2 WBS 网络图 — 单个任务节点。
 *
 * 设计: 网络图要画得有意义, 节点除了身份信息外, 还要带:
 *  - 进度 (color/border 用)
 *  - 里程碑标记 (菱形 vs 圆)
 *  - 计划工期 (天数, 节点尺寸参考)
 *  - 关键路径标记 (onCritical, P3.3 算)
 *
 * @param taskId       WbsTask id (GraphChart 用做 id)
 * @param wbsCode      树编码
 * @param name         任务名
 * @param status       NOT_STARTED / IN_PROGRESS / ...
 * @param progressPct  0-100
 * @param milestone    是否里程碑
 * @param critical     是否关键路径 (P3.3 标)
 * @param planStart    计划开始
 * @param planEnd      计划结束
 * @param planDurationDays 工期天数 (planEnd - planStart + 1), null 表示无
 * @param planHours    计划工时
 * @param ownerName    负责人名 (可能 null)
 */
public record WbsNetworkNode(
        Long taskId,
        String wbsCode,
        String name,
        String status,
        Integer progressPct,
        boolean milestone,
        boolean critical,
        LocalDate planStart,
        LocalDate planEnd,
        Integer planDurationDays,
        BigDecimal planHours,
        String ownerName
) {}
