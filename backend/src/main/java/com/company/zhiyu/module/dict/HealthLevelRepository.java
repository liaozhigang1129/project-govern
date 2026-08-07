package com.company.zhiyu.module.dict;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthLevelRepository extends JpaRepository<HealthLevel, Long> {

    java.util.Optional<HealthLevel> findByCode(String code);
    boolean existsByCode(String code);

}
