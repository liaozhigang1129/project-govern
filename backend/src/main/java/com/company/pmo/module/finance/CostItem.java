package com.company.pmo.module.finance;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F3: 成本项 (P1 - 项目级成本归集)
 *
 *  - project: 必填 (所有金额都要落到项目)
 *  - type: 成本类型 (人力/采购/差旅/服务/其他)
 *  - source: 数据来源 (HOURS_AUTO=工时自动 / CONTRACT=合同 / INVOICE=发票 / MANUAL=手工)
 *  - 3 FK: contract_id / invoice_id / payment_id (审计追溯链)
 */
@Entity
@Table(name = "cost_item", indexes = {
        @Index(name = "idx_cost_item_project_date", columnList = "project_id, date"),
        @Index(name = "idx_cost_item_contract", columnList = "contract_id"),
        @Index(name = "idx_cost_item_invoice", columnList = "invoice_id"),
        @Index(name = "idx_cost_item_payment", columnList = "payment_id"),
        @Index(name = "idx_cost_item_type", columnList = "type")
})
@Getter @Setter @NoArgsConstructor
public class CostItem extends SoftDeletableEntity {

    public enum Type { LABOR, PURCHASE, TRAVEL, SERVICE, OTHER }
    public enum Source { HOURS_AUTO, CONTRACT, INVOICE, MANUAL }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "project_id", nullable = false) private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Type type;

    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;

    @Column(nullable = false) private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Source source = Source.MANUAL;

    @Column(name = "contract_id") private Long contractId;
    @Column(name = "invoice_id") private Long invoiceId;
    @Column(name = "payment_id") private Long paymentId;

    @Column(name = "user_id") private Long userId;     // LABOR 时填

    @Column(columnDefinition = "text") private String remark;
}
