package com.company.zhiyu.module.finance;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F3: 付款 (P1)
 *
 *  - code: 付款单号 (业务唯一)
 *  - invoice: 必填 (1 张发票 1..N 次付款, 例如分批付款)
 *  - status: PENDING → CONFIRMED / REJECTED
 *  - bankRef: 银行流水号 (防重复付款)
 */
@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_payment_invoice", columnList = "invoice_id"),
        @Index(name = "idx_payment_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor
public class Payment extends SoftDeletableEntity {

    public enum Status { PENDING, CONFIRMED, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(nullable = false, unique = true, length = 64) private String code;

    @Column(name = "invoice_id", nullable = false) private Long invoiceId;

    @Column(name = "payment_date", nullable = false) private LocalDate paymentDate;

    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Status status = Status.PENDING;

    @Column(name = "bank_ref", length = 128) private String bankRef;

    @Column(name = "approver_user_id") private Long approverUserId;

    @Column(columnDefinition = "text") private String remark;
}
