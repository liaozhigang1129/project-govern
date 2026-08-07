package com.hex.projectgovern.module.wbs.dto;

/**
 * WBS 任务移动/重排请求 — P4-WBS 拖拽重排 (a + b 步) 共用 body。
 * <p>被 WbsController 的 {@code PATCH /wbs/tasks/{id}/move} 消费。
 * <p>由 Service 内部根据字段组合 dispatch 到 a 步或 b 步:
 * <ul>
 *   <li><b>a 步 (moveTask)</b>: {@code beforeSiblingId == null} — 换 parent (拖到节点上)
 *     <ul>
 *       <li>{@code newParentId == null} → 拖到项目顶层</li>
 *       <li>{@code newParentId != null} → 拖到指定任务下, Service 会做跨项目/防环校验</li>
 *     </ul>
 *   </li>
 *   <li><b>b 步 (reorderWithinParent)</b>: {@code beforeSiblingId != null} — 同 parent 内重排 (拖到节点前/后)
 *     <ul>
 *       <li>{@code newParentId == null} → 顶层同级重排 (调整项目根节点顺序)</li>
 *       <li>{@code newParentId != null} → 指定 parent 的子级重排 (调整某任务的子节点顺序)</li>
 *       <li>若新 parentId 跟 task 当前 parentId 不同, 仍然走 b 步 (等于"换 parent + 落到目标位置"组合)</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>前端 el-tree dropPosition 决策:
 * <pre>{@code
 *   dropPosition="inner" (拖到节点上, 变新子)
 *      → body={newParentId: 落点节点 id}
 *   dropPosition="before" / "after" (拖到节点前/后间隙)
 *      → body={newParentId: 落点节点 parentId, beforeSiblingId: 落点节点 id (before) 或 后一个 id (after)}
 * }</pre>
 *
 * <p>字段都是可空 — null 是 first-class value:
 * <ul>
 *   <li>{@code newParentId == null} → 拖到顶层</li>
 *   <li>{@code beforeSiblingId == null} → 末尾 / 走 a 步</li>
 * </ul>
 *
 * <p>校验全部下沉到 Service (跨项目/防环/sibling 越界).
 *
 * @since 2026-Q1 P4-WBS
 */
public record WbsTaskMoveRequest(
        Long newParentId,       // 目标父 id, null = 顶层
        Long beforeSiblingId    // 拖到该 sibling 之前, null = 末尾 / 走 a 步
) {
}
