package com.company.pmo.module.dict;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RelatedProductRepository extends JpaRepository<RelatedProduct, Long> {
    List<RelatedProduct> findAllByDeletedFalseOrderBySortOrderAscIdAsc();
    List<RelatedProduct> findAllByPlIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long plId);
    Optional<RelatedProduct> findByIdAndDeletedFalse(Long id);
    boolean existsByCodeAndDeletedFalse(String code);
}
