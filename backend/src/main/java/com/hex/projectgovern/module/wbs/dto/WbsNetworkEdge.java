package com.hex.projectgovern.module.wbs.dto;

/**
 * P3.2 WBS 网络图 — 一条有向边 (A → B 表示 A 是 B 的紧前)。
 *
 * @param fromTaskId    起点任务 (紧前) id
 * @param toTaskId      终点任务 id
 * @param isCriticalEdge 边是否在关键路径上 (P3.3 标)
 */
public record WbsNetworkEdge(
        Long fromTaskId,
        Long toTaskId,
        boolean isCriticalEdge
) {}
