package com.hex.projectgovern.module.timesheet;

import lombok.Getter;

/**
 * V4.34 工时自动填报 - 项目匹配规则优先级枚举
 *
 * 优先级 (数字越小越优先):
 *   1. PM         — 项目 status=ACTIVE 且 project.pm_user_id = user.id
 *   2. BU         — project.bu_id = user.department 关联的 BU (通过 dept 上溯至 BU 节点)
 *   3. PL         — project.pl_id = user.department 关联的 PL
 *   4. DEPT_GROUP — project.department_id ∈ user.department 的所有子部门 (含自身)
 *   5. WBS        — wbs_assignment.user_id = userId 且 assignment.start_date <= day <= assignment.end_date
 *   6. PLACEHOLDER — 兜底, 无任何候选时使用
 */
@Getter
public enum AutoFillMatchReason {
    PM(1, "我是 PM"),
    BU(2, "我 BU 的项目"),
    PL(3, "我 PL 的项目"),
    DEPT_GROUP(4, "我部门项目组"),
    WBS(5, "我分配的 WBS 任务"),
    PLACEHOLDER(6, "无候选项目(占位)");

    private final int priority;
    private final String label;

    AutoFillMatchReason(int priority, String label) {
        this.priority = priority;
        this.label = label;
    }

    public String description() {
        return "[自动填报] " + label;
    }
}
