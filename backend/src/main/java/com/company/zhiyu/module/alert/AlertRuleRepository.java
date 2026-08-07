package com.company.zhiyu.module.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    Optional<AlertRule> findByCodeAndDeletedFalse(String code);

    List<AlertRule> findByEnabledTrueAndDeletedFalseOrderById();

    @Query("""
        SELECT r FROM AlertRule r
        WHERE r.typeCode = :typeCode
          AND r.enabled = true
          AND r.deleted = false
        """)
    List<AlertRule> findEnabledByType(@Param("typeCode") String typeCode);
}
