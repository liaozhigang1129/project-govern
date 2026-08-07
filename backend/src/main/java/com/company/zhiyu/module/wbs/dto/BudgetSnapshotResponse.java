package com.company.zhiyu.module.wbs.dto;

import com.company.zhiyu.module.wbs.BudgetSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * EVM 快照响应 — 不可变历史。
 * <p>对齐 {@code budget_snapshot} 表, 触发器已禁止 UPDATE/DELETE。
 */
public record BudgetSnapshotResponse(
        Long id,
        Long projectId,
        LocalDate snapshotDate,
        Integer version,
        String reason,
        BigDecimal bac, BigDecimal pv, BigDecimal ev, BigDecimal ac,
        BigDecimal cpi, BigDecimal spi, BigDecimal eac, BigDecimal etc, BigDecimal vac,
        Long createdBy,
        Instant createdAt
) {
    public static BudgetSnapshotResponse from(BudgetSnapshot s) {
        return new BudgetSnapshotResponse(
                s.getId(), s.getProjectId(), s.getSnapshotDate(), s.getVersion(), s.getReason(),
                s.getBac(), s.getPv(), s.getEv(), s.getAc(),
                s.getCpi(), s.getSpi(), s.getEac(), s.getEtc(), s.getVac(),
                s.getCreatedBy(),
                s.getCreatedAt()
        );
    }
}
