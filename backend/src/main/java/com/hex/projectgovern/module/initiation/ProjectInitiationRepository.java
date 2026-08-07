package com.hex.projectgovern.module.initiation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ProjectInitiationRepository extends JpaRepository<ProjectInitiation, Long>, JpaSpecificationExecutor<ProjectInitiation> {
    List<ProjectInitiation> findByDeletedFalseOrderByCreatedAtDesc();
    boolean existsByCodeAndDeletedFalse(String code);

    /** 一次性把 status 拉出来,避免 controller 序列化时 LAZY 抛 no Session */
    @org.springframework.data.jpa.repository.Query("SELECT i FROM ProjectInitiation i JOIN FETCH i.status WHERE i.deleted = false ORDER BY i.createdAt DESC")
    List<ProjectInitiation> findAllActiveWithStatus();

    /** V4.19: 单条查询也 JOIN FETCH status (供 PATCH /initiations/{id} 用) */
    @org.springframework.data.jpa.repository.Query("SELECT i FROM ProjectInitiation i JOIN FETCH i.status WHERE i.id = :id AND i.deleted = false")
    java.util.Optional<ProjectInitiation> findActiveById(@org.springframework.data.repository.query.Param("id") Long id);
}
