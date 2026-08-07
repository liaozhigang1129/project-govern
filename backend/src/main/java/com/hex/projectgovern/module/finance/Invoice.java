package com.hex.projectgovern.module.finance;

import com.hex.projectgovern.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F3: 发票 (P1)
 *
 *  - code: 发票号 (业务唯一, 同一合同下也唯一)
 *  - contract: PENDING 时可空 (未匹配), MATCHED 时必填
 *  - status: PENDING → MATCHED → PAID (状态机)
 *  - matchStrategy: AUTO (按金额+合同号启发式) / MANUAL (财务手工)
 */
@Entity
@Table(name = "invoice",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_invoice_code", columnNames = "code"),
           @UniqueConstraint(name = "uq_invoice_contract_code", columnNames = {"contract_id", "code"})
       },
       indexes = {
           @Index(name = "idx_invoice_status", columnList = "status"),
           @Index(name = "idx_invoice_contract", columnList = "contract_id"),
           @Index(name = "idx_invoice_date", columnList = "invoice_date")
       })
@Getter @Setter @NoArgsConstructor
public class Invoice extends SoftDeletableEntity {

    public enum Status { PENDING, MATCHED, PAID, REJECTED }
    public enum MatchStrategy { AUTO, MANUAL }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(nullable = false, unique = true, length = 64) private String code;

    @Column(name = "contract_id") private Long contractId;

    @Column(name = "vendor_id") private Long vendorId;
    @Column(name = "vendor_name", length = 128) private String vendorName;

    @Column(name = "invoice_date", nullable = false) private LocalDate invoiceDate;

    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "tax_amount", precision = 14, scale = 2) private BigDecimal taxAmount;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_strategy", length = 16) private MatchStrategy matchStrategy;

    @Column(name = "matched_at") private LocalDate matchedAt;
    @Column(name = "matched_by_user_id") private Long matchedByUserId;

    @Column(name = "file_url", length = 512) private String fileUrl;

    @Column(columnDefinition = "text") private String remark;
}
