package com.company.pmo.module.initiation;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 立项预算快照 + 毛利(Step 6 终态基线)。
 * <p>对齐 V3.0 {@code initiation_budget_freeze} 表。
 * <p>业务约束:同一 initiation_id 唯一(由 uk_init_budget_freeze_active 保证),
 *              重新冻结 = 删除旧快照 + 写新快照(或在 service 中先软删)。
 * <p>字段语义:
 * <ul>
 *   <li>contractAmount — 合同金额(由 project_initiation.contract_amount 抄过来,允许覆盖)</li>
 *   <li>resourceCost   — sum(资源派遣计划.cost_amount)</li>
 *   <li>riskCost       — sum(风险应对.response_cost)</li>
 *   <li>otherCost      — 用户在 UI 中调整的其它预算(差旅/采购/...</li>
 *   <li>totalCost      — resource + risk + other</li>
 *   <li>margin         — contractAmount - totalCost</li>
 *   <li>marginPct      — (margin / contractAmount) × 100</li>
 * </ul>
 */
@Entity
@Table(name = "initiation_budget_freeze",
        uniqueConstraints = @UniqueConstraint(name = "uk_init_budget_freeze_active", columnNames = "initiation_id"))
@Getter @Setter @NoArgsConstructor
public class InitiationBudgetFreeze extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "initiation_id", nullable = false)
    private Long initiationId;

    @Column(name = "frozen_by", nullable = false)
    private Long frozenBy;

    @Column(name = "frozen_at", nullable = false)
    private Instant frozenAt = Instant.now();

    @Column(name = "contract_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal contractAmount = BigDecimal.ZERO;

    @Column(name = "resource_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal resourceCost = BigDecimal.ZERO;

    @Column(name = "risk_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal riskCost = BigDecimal.ZERO;

    @Column(name = "other_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherCost = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "margin", nullable = false, precision = 14, scale = 2)
    private BigDecimal margin = BigDecimal.ZERO;

    @Column(name = "margin_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal marginPct = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "json")
    private String snapshotJson;
}
