package com.company.pmo.module.wbs.dto;

import java.util.List;
import java.util.function.Predicate;

/**
 * P3.2 WBS 网络图 (前置依赖图) 响应。
 *
 * 节点: 任务 (含状态/进度/里程碑/关键路径)
 * 边:   紧前关系 A → B 表示 A 是 B 的前置
 *       关键路径上的边用 isCriticalEdge=true 标出 (P3.3 复用)
 *
 * @param projectId   项目 id
 * @param taskCount   节点数
 * @param nodes       节点列表
 * @param edges       边列表
 * @param criticalTaskIds 关键路径上的任务 id (P3.3 算出来, 高亮展示)
 */
public record WbsNetworkResponse(
        Long projectId,
        int taskCount,
        List<WbsNetworkNode> nodes,
        List<WbsNetworkEdge> edges,
        List<Long> criticalTaskIds
) {
    /** 复用谓词 — 流式过滤"关键路径边"。 */
    public static Predicate<WbsNetworkEdge> criticalEdgeFilter() {
        return WbsNetworkEdge::isCriticalEdge;
    }
}
