package com.hex.projectgovern.module.finance;

import com.hex.projectgovern.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * F3: 财务-成本 3-way match 对账快照 (V5.0)
 *
 * <p>对账逻辑:
 * <ul>
 *   <li>合同额 ↔ 开票价税合计 ↔ 实付额 ↔ 入账成本 (按 project + period 聚合)</li>
 *   <li>容差内 = {@link MatchStatus#MATCHED}; 部分缺失 = {@link MatchStatus#PARTIAL};
 *       差异 > 阈值 = {@link MatchStatus#MISMATCH}; 数据不全 = {@link MatchStatus#PENDING}</li>
 *   <li>幂等键 = (project, contract, invoice, payment, cost_item, period)</li>
 * </ul>
 *
 * @since V5.0 / WP-M4-03
 */
@Entity
@Table(name = "cost_reconciliation",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_cost_recon",
           columnNames = {"project_id", "contract_id", "invoice_id", "payment_id", "cost_item_id", "period"}),
       indexes = {
           @Index(name = "idx_cost_recon_project_status", columnList = "project_id,match_status"),
           @Index(name = "idx_cost_recon_reconciled_at",  columnList = "reconciled_at"),
           @Index(name = "idx_cost_recon_project_period", columnList = "project_id,period"),
           @Index(name = "idx_cost_recon_status_diff",    columnList = "match_status,diff_amount")
       })
@Getter @Setter @NoArgsConstructor
public class CostReconciliation extends SoftDeletableEntity {

    public enum MatchStatus { MATCHED, PARTIAL, MISMATCH, PENDING }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "project_id", nullable = false) private Long projectId;

    @Column(name = "contract_id")  private Long contractId;
    @Column(name = "invoice_id")   private Long invoiceId;
    @Column(name = "payment_id")   private Long paymentId;
    @Column(name = "cost_item_id") private Long costItemId;

    /** 对账期间 YYYY-MM */
    @Column(nullable = false, length = 7) private String period;

    @Column(name = "contract_amount", nullable = false, precision = 14, scale = 2) private BigDecimal contractAmount = BigDecimal.ZERO;
    @Column(name = "invoice_amount",  nullable = false, precision = 14, scale = 2) private BigDecimal invoiceAmount  = BigDecimal.ZERO;
    @Column(name = "payment_amount",  nullable = false, precision = 14, scale = 2) private BigDecimal paymentAmount  = BigDecimal.ZERO;
    @Column(name = "cost_amount",     nullable = false, precision = 14, scale = 2) private BigDecimal costAmount     = BigDecimal.ZERO;

    @Column(name = "diff_amount", nullable = false, precision = 14, scale = 2) private BigDecimal diffAmount = BigDecimal.ZERO;
    @Column(name = "diff_reason", columnDefinition = "text") private String diffReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 16)
    private MatchStatus matchStatus = MatchStatus.PENDING;

    @Column(name = "reconciled_at") private Instant reconciledAt;
    @Column(name = "reconciled_by") private Long reconciledBy;

    /** 便捷:从 LocalDate 取 period 字符串 */
    public static String periodOf(LocalDate date) {
        return date == null ? null : date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
}
