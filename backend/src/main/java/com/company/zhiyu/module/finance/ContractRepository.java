package com.company.zhiyu.module.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByIdAndDeletedFalse(Long id);

    Optional<Contract> findByCodeAndDeletedFalse(String code);

    List<Contract> findAllByDeletedFalseOrderByIdDesc();

    List<Contract> findAllByStatusAndDeletedFalseOrderByIdDesc(Contract.Status status);

    List<Contract> findAllByProjectIdAndDeletedFalseOrderByIdDesc(Long projectId);

    /** 合同已付金额 (CONFIRMED payment JOIN invoice) */
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payment p
        JOIN Invoice i ON i.id = p.invoiceId
        WHERE i.contractId = :cid
          AND p.status = com.company.zhiyu.module.finance.Payment.Status.CONFIRMED
          AND p.deleted = false
          AND i.deleted = false
    """)
    BigDecimal sumPaidAmount(@Param("cid") Long contractId);
}
