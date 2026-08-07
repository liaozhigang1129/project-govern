package com.company.zhiyu.module.wbs.dto;

import com.company.zhiyu.module.wbs.WbsTask;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * WBS 任务响应(树节点用)。
 * <p>前端 el-tree 期望的字段: id / label (走 name) / children / disabled (按 status)。
 * 这里 children 用 List&lt;WbsTaskNode&gt; 嵌套, 一次到位, 不需要前端再拼。
 * <p>{@code depth} 0=根, 1=一级子任务, ...
 * <p>{@code path} 路径编码数组, 方便面包屑显示 (e.g. ["1","1.1","1.1.2"])。
 */
public record WbsTaskNode(
        Long id,
        Long projectId,
        Long parentId,
        String wbsCode,
        String name,
        String taskType,
        String status,
        Long ownerUserId,
        LocalDate planStartDate,
        LocalDate planEndDate,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        BigDecimal planHours,
        BigDecimal actualHours,
        Integer progressPct,
        Integer weight,
        boolean critical,
        boolean milestone,
        Long milestoneId,
        List<Long> predecessorIds,
        String deliverable,
        String remark,
        Instant createdAt,
        Instant updatedAt,
        int depth,
        List<String> path,
        List<WbsTaskNode> children
) {
    public static WbsTaskNode leaf(WbsTask t, int depth, List<String> path) {
        return new WbsTaskNode(
                t.getId(), t.getProjectId(), t.getParentId(), t.getWbsCode(), t.getName(),
                t.getTaskType(), t.getStatus(), t.getOwnerUserId(),
                t.getPlanStartDate(), t.getPlanEndDate(),
                t.getActualStartDate(), t.getActualEndDate(),
                t.getPlanHours(), t.getActualHours(),
                t.getProgressPct(), t.getWeight(),
                t.isCritical(), t.isMilestone(), t.getMilestoneId(),
                t.predecessorIdList(),
                t.getDeliverable(), t.getRemark(),
                t.getCreatedAt(), t.getUpdatedAt(),
                depth, path, new ArrayList<>()
        );
    }
}
