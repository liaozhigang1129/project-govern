package com.company.zhiyu.module.wbs;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 项目预算分项 (BAC/承诺/实际 三段式)。
 * <p>对齐 V2.5 {@code budget_line} 表。
 * <ul>
 *   <li>planned_amount   — BAC 的一部分 (完工预算分项)</li>
 *   <li>committed_amount — 已承诺 (合同/PO)</li>
 *   <li>actual_amount    — 已实际发生</li>
 * </ul>
 * EVM 的 PV/EV/AC 通过 {@code budget_snapshot} 历史快照留存,
 * 不在本表上累积, 避免每次花销都改 budget_line。
 */
@Entity
@Table(name = "budget_line", uniqueConstraints = {
        @UniqueConstraint(name = "uk_budget_line_project_category", columnNames = {"project_id", "category"})
})
@Getter @Setter @NoArgsConstructor
public class BudgetLine extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false) private Long projectId;

    /** 可空: 任务级预算挂具体 WBS 任务, 项目级预算 (e.g. 差旅/预留金) 不挂 */
    @Column(name = "wbs_task_id") private Long wbsTaskId;

    @Column(nullable = false, length = 32) private String category;
    // LABOR / PURCHASE / TRAVEL / CONTINGENCY / OTHER

    @Column(nullable = false, length = 128) private String name;

    @Column(name = "planned_amount",   nullable = false, precision = 14, scale = 2)
    private BigDecimal plannedAmount = BigDecimal.ZERO;

    @Column(name = "committed_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal committedAmount = BigDecimal.ZERO;

    @Column(name = "actual_amount",    nullable = false, precision = 14, scale = 2)
    private BigDecimal actualAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    private String currency = "CNY";

    @Column(name = "created_by") private Long createdBy;

    @Column(columnDefinition = "text") private String note;
}
