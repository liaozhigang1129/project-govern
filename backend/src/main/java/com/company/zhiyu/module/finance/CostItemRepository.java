package com.company.zhiyu.module.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CostItemRepository extends JpaRepository<CostItem, Long> {

    Optional<CostItem> findByIdAndDeletedFalse(Long id);

    List<CostItem> findAllByProjectIdAndDeletedFalseOrderByDateDesc(Long projectId);

    List<CostItem> findAllByContractIdAndDeletedFalseOrderByDateDesc(Long contractId);

    List<CostItem> findAllByInvoiceIdAndDeletedFalseOrderByDateDesc(Long invoiceId);

    @Query("""
        SELECT COALESCE(SUM(amount), 0)
        FROM CostItem c
        WHERE c.projectId = :pid
          AND c.deleted = false
          AND c.date BETWEEN :from AND :to
    """)
    BigDecimal sumByProjectAndDateRange(
            @Param("pid") Long projectId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
