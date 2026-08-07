package com.company.zhiyu.module.wbs.dto;

import java.util.List;

/**
 * P3.3 WBS 甘特图响应: 一次性拉项目的所有可绘制 WbsTask, 含自动算出的坐标轴。
 * <p>对应项目级 GanttResponse, 字段命名保持一致, 方便前端 adapter 直接复用 GanttView。
 *
 * @param projectId   项目 id
 * @param rangeFrom   任务 plan 区间最早值 - 7d (无任务时 = today - 30d)
 * @param rangeTo     任务 plan 区间最晚值 + 7d (无任务时 = today + 60d)
 * @param taskCount   实际可绘制的任务数 (有 plan 区间且未软删的)
 * @param rows        按 wbsCode 升序的任务行
 */
public record WbsGanttResponse(
        Long projectId,
        String rangeFrom,
        String rangeTo,
        int taskCount,
        List<WbsGanttRow> rows
) {
}
