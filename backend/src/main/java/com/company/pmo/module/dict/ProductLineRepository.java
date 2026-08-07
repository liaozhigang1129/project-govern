package com.company.pmo.module.dict;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductLineRepository extends JpaRepository<ProductLine, Long> {
    List<ProductLine> findAllByDeletedFalseOrderBySortOrderAscIdAsc();
    List<ProductLine> findAllByBuIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long buId);
    Optional<ProductLine> findByIdAndDeletedFalse(Long id);
    boolean existsByCodeAndDeletedFalse(String code);
}
