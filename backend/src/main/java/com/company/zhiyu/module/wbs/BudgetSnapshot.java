package com.company.zhiyu.module.wbs;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * EVM 预算/进度 历史快照 — 不可变(append-only), 由触发器禁止 UPDATE/DELETE。
 * <p>对齐 V2.5 {@code budget_snapshot} 表。
 * CPI/SPI/健康度等指标历史走势直接读此表, 不每次重算。
 */
@Entity
@Table(name = "budget_snapshot")
@Getter @Setter @NoArgsConstructor
public class BudgetSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "snapshot_date", nullable = false) private LocalDate snapshotDate;

    @Column(nullable = false) private Integer version = 1;
    @Column(length = 256) private String reason;

    // 预算核心 (BAC/PV/EV/AC)
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal bac = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal pv  = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal ev  = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal ac  = BigDecimal.ZERO;

    // 派生指标
    @Column(nullable = false, precision = 6, scale = 3) private BigDecimal cpi = BigDecimal.ONE;
    @Column(nullable = false, precision = 6, scale = 3) private BigDecimal spi = BigDecimal.ONE;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal eac = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal etc = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal vac = BigDecimal.ZERO;

    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
}
