package com.company.zhiyu.module.wbs.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * WBS 甘特图单行(给 P3.3 复用 GanttView 用)。
 * <p>对应一条 WbsTask, 含自身 plan/actual 区间, 进度, 负责人。
 * <p>对齐 GanttBar 字段 (projectId → taskId, projectCode → wbsCode),
 * 方便前端在 adapter 里直接 map 进 GanttView 期望的 GanttResponse 形状。
 *
 * @param taskId        WbsTask id
 * @param wbsCode       树编码 (e.g. "1.2.3")
 * @param name          任务名
 * @param depth         树深度, 用于前端缩进
 * @param parentId      父任务 id
 * @param taskType      SUMMARY / EXECUTION / MILESTONE / DELIVERABLE
 * @param status        NOT_STARTED / IN_PROGRESS / COMPLETED / ...
 * @param ownerUserId   负责人 id (可能 null)
 * @param ownerName     负责人姓名 (前端拿来展示, 可能 null)
 * @param planStart     计划开始
 * @param planEnd       计划结束
 * @param actualStart   实际开始 (可能 null)
 * @param actualEnd     实际结束 (可能 null)
 * @param progressPct   0-100
 * @param weight        1-10
 * @param critical      关键路径
 * @param milestone     里程碑标记 (前端会画成菱形)
 */
public record WbsGanttRow(
        Long taskId,
        String wbsCode,
        String name,
        int depth,
        Long parentId,
        String taskType,
        String status,
        Long ownerUserId,
        String ownerName,
        LocalDate planStart,
        LocalDate planEnd,
        LocalDate actualStart,
        LocalDate actualEnd,
        Integer progressPct,
        Integer weight,
        boolean critical,
        boolean milestone,
        BigDecimal planHours,
        BigDecimal actualHours
) {
}
