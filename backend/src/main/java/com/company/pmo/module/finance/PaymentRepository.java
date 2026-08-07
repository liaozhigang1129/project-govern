package com.company.pmo.module.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdAndDeletedFalse(Long id);

    Optional<Payment> findByCodeAndDeletedFalse(String code);

    List<Payment> findAllByDeletedFalseOrderByIdDesc();

    List<Payment> findAllByInvoiceIdAndDeletedFalseOrderByIdDesc(Long invoiceId);

    List<Payment> findAllByStatusAndDeletedFalseOrderByIdDesc(Payment.Status status);
}
