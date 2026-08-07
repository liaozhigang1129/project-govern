package com.hex.projectgovern.module.dict;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectLevelRepository extends JpaRepository<ProjectLevel, Long> {

    java.util.Optional<ProjectLevel> findByCode(String code);

    /** 按 sort_order asc 列出 (前端下拉用) */
    List<ProjectLevel> findAllByOrderBySortOrderAsc();
}
