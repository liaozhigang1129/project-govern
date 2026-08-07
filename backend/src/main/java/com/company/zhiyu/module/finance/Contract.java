package com.company.zhiyu.module.finance;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * F3: 合同台账 (P1)
 *
 *  - code: 业务唯一 (HT-2026-001)
 *  - project: 可选 (单合同可关联多项目, 但通常 1:1)
 *  - status: DRAFT/ACTIVE/CLOSED/TERMINATED
 *  - amount: 合同总金额
 */
@Entity
@Table(name = "contract", indexes = {
        @Index(name = "idx_contract_status", columnList = "status"),
        @Index(name = "idx_contract_project", columnList = "project_id"),
        @Index(name = "idx_contract_owner", columnList = "owner_user_id")
})
@Getter @Setter @NoArgsConstructor
public class Contract extends SoftDeletableEntity {

    public enum Status { DRAFT, ACTIVE, CLOSED, TERMINATED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(nullable = false, unique = true, length = 64) private String code;
    @Column(nullable = false, length = 256) private String name;

    @Column(name = "vendor_id") private Long vendorId;
    @Column(name = "vendor_name", length = 128) private String vendorName;

    @Column(name = "project_id") private Long projectId;

    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Status status = Status.DRAFT;

    @Column(name = "sign_date") private LocalDate signDate;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;

    @Column(name = "owner_user_id") private Long ownerUserId;

    @Column(columnDefinition = "text") private String remark;
}
