package com.company.pmo.module.opportunityfunnel;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** P6 商机 */
@Entity
@Table(name = "opportunity")
@Getter @Setter @NoArgsConstructor
public class Opportunity extends SoftDeletableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 32, unique = true) private String code;
    @Column(nullable = false, length = 256) private String name;
    @Column(name = "customer_name", nullable = false, length = 128) private String customerName;
    @Column(name = "customer_contact", length = 64) private String customerContact;
    @Column(name = "bu_id") private Long buId;
    @Column(name = "pl_id") private Long plId;
    @Column(name = "owner_user_id", nullable = false) private Long ownerUserId;
    @Column(nullable = false, length = 16) private String stage = "LEAD";
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount = BigDecimal.ZERO;
    @Column(name = "cost_estimate", precision = 14, scale = 2) private BigDecimal costEstimate;
    @Column(nullable = false, precision = 4, scale = 2) private BigDecimal probability = new BigDecimal("0.10");
    @Column(name = "expected_close") private LocalDate expectedClose;
    @Column(name = "actual_close") private LocalDate actualClose;
    @Column(length = 32) private String source;
    @Column(name = "lead_date", nullable = false) private LocalDate leadDate;
    @Column(nullable = false, length = 16) private String status = "OPEN";
    @Column(length = 500) private String remark;
    @Column(name = "created_by", nullable = false) private Long createdBy;
}
