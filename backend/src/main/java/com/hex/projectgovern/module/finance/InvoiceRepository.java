package com.hex.projectgovern.module.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByIdAndDeletedFalse(Long id);

    Optional<Invoice> findByCodeAndDeletedFalse(String code);

    List<Invoice> findAllByDeletedFalseOrderByIdDesc();

    List<Invoice> findAllByStatusAndDeletedFalseOrderByIdDesc(Invoice.Status status);

    List<Invoice> findAllByContractIdAndDeletedFalseOrderByIdDesc(Long contractId);

    List<Invoice> findAllByVendorIdAndDeletedFalseOrderByIdDesc(Long vendorId);

    /** 合同下发票总金额 (MATCHED+PAID) */
    @Query("""
        SELECT COALESCE(SUM(totalAmount), 0)
        FROM Invoice i
        WHERE i.contractId = :cid
          AND i.status IN (com.hex.projectgovern.module.finance.Invoice.Status.MATCHED,
                           com.hex.projectgovern.module.finance.Invoice.Status.PAID)
          AND i.deleted = false
    """)
    BigDecimal sumInvoicedAmount(@Param("cid") Long contractId);

    /** AUTO 匹配候选: 同 vendor + 未匹配 + 总金额在合同余额内 */
    @Query("""
        SELECT i FROM Invoice i
        WHERE i.contractId IS NULL
          AND i.deleted = false
          AND i.status = com.hex.projectgovern.module.finance.Invoice.Status.PENDING
        ORDER BY i.id DESC
    """)
    List<Invoice> findUnmatchedPending();
}
