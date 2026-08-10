package com.hex.projectgovern.module.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CostReconciliationRepository
        extends JpaRepository<CostReconciliation, Long> {

    Optional<CostReconciliation> findByIdAndDeletedFalse(Long id);

    Optional<CostReconciliation> findByProjectIdAndContractIdAndInvoiceIdAndPaymentIdAndCostItemIdAndPeriodAndDeletedFalse(
            Long projectId, Long contractId, Long invoiceId,
            Long paymentId, Long costItemId, String period);

    List<CostReconciliation> findAllByProjectIdAndDeletedFalseOrderByReconciledAtDesc(Long projectId);

    /**
     * 多条件分页查询
     * @param projectId  按项目筛选 (可空)
     * @param status     按状态筛选 (可空)
     * @param from       对账时间下界 (可空)
     * @param to         对账时间上界 (可空)
     */
    @Query("""
        SELECT r FROM CostReconciliation r
        WHERE r.deleted = false
          AND (:projectId IS NULL OR r.projectId = :projectId)
          AND (:status    IS NULL OR r.matchStatus = :status)
          AND (:from      IS NULL OR r.reconciledAt >= :from)
          AND (:to        IS NULL OR r.reconciledAt <  :to)
    """)
    Page<CostReconciliation> search(
            @Param("projectId") Long projectId,
            @Param("status") CostReconciliation.MatchStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /** 项目对账健康度聚合 */
    @Query("""
        SELECT new com.hex.projectgovern.module.finance.dto.FinanceDtos$ReconciliationHealth(
            COUNT(r),
            SUM(CASE WHEN r.matchStatus = com.hex.projectgovern.module.finance.CostReconciliation$MatchStatus.MATCHED THEN 1 ELSE 0 END),
            SUM(CASE WHEN r.matchStatus = com.hex.projectgovern.module.finance.CostReconciliation$MatchStatus.MISMATCH THEN 1 ELSE 0 END),
            SUM(CASE WHEN r.matchStatus = com.hex.projectgovern.module.finance.CostReconciliation$MatchStatus.PARTIAL  THEN 1 ELSE 0 END),
            SUM(CASE WHEN r.matchStatus = com.hex.projectgovern.module.finance.CostReconciliation$MatchStatus.PENDING  THEN 1 ELSE 0 END),
            COALESCE(SUM(r.diffAmount), 0)
        )
        FROM CostReconciliation r
        WHERE r.deleted = false
          AND (:projectId IS NULL OR r.projectId = :projectId)
    """)
    com.hex.projectgovern.module.finance.dto.FinanceDtos.ReconciliationHealth health(
            @Param("projectId") Long projectId);

    /** 全公司差异总额(单查) */
    @Query("""
        SELECT COALESCE(SUM(diffAmount), 0) FROM CostReconciliation r
        WHERE r.deleted = false
          AND r.matchStatus = com.hex.projectgovern.module.finance.CostReconciliation$MatchStatus.MISMATCH
          AND (:projectId IS NULL OR r.projectId = :projectId)
    """)
    BigDecimal sumDiffByMismatch(@Param("projectId") Long projectId);
}
