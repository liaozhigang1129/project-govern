package com.company.zhiyu.module.dict;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectStatusRepository extends JpaRepository<ProjectStatus, Long> {

    java.util.Optional<ProjectStatus> findByCode(String code);
    boolean existsByCode(String code);

}
