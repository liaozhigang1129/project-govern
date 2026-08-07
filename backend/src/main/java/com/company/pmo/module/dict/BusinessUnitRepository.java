package com.company.pmo.module.dict;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessUnitRepository extends JpaRepository<BusinessUnit, Long> {
    List<BusinessUnit> findAllByDeletedFalseOrderBySortOrderAscIdAsc();
    boolean existsByCodeAndDeletedFalse(String code);
}
