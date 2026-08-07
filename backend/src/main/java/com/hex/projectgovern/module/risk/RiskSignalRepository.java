package com.hex.projectgovern.module.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RiskSignalRepository extends JpaRepository<RiskSignal, Long> {

    /** 加载所有 enabled 信号 (cache 全量预加载使用) */
    @Query("SELECT s FROM RiskSignal s WHERE s.enabled = true ORDER BY s.id ASC")
    List<RiskSignal> findAllEnabled();

    /**
     * V4.31 软删除列补齐: cache 加载只取未删除的信号.
     * 旧方法 {@link #findAllEnabled()} 保留 (兼容旧调用).
     */
    @Query("SELECT s FROM RiskSignal s WHERE s.enabled = true AND s.deleted = false ORDER BY s.id ASC")
    List<RiskSignal> findAllEnabledAndNotDeleted();

    /** 按桶 code 查询 (前端编辑桶时展示) */
    List<RiskSignal> findByBucketCodeOrderByIdAsc(String bucketCode);

    /** 按桶 code 查询未删除的 (前端编辑桶时只展示活的) */
    List<RiskSignal> findByBucketCodeAndDeletedFalseOrderByIdAsc(String bucketCode);

    /** 查重: 同桶下 keyword 已存在? */
    boolean existsByBucketCodeAndKeyword(String bucketCode, String keyword);
}
