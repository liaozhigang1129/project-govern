package com.company.pmo.module.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RiskTemplateRepository extends JpaRepository<RiskTemplate, Long> {

    /** 加载所有 enabled 模板 (cache 全量预加载使用) */
    @Query("SELECT t FROM RiskTemplate t WHERE t.enabled = true ORDER BY t.sortOrder ASC, t.id ASC")
    List<RiskTemplate> findAllEnabled();

    /**
     * V4.31 软删除列补齐: cache 加载只取未删除的模板.
     * 旧方法 {@link #findAllEnabled()} 保留 (兼容旧调用).
     */
    @Query("SELECT t FROM RiskTemplate t WHERE t.enabled = true AND t.deleted = false ORDER BY t.sortOrder ASC, t.id ASC")
    List<RiskTemplate> findAllEnabledAndNotDeleted();

    /** 按桶 code 查询 (前端编辑桶时展示) */
    List<RiskTemplate> findByBucketCodeOrderBySortOrderAscIdAsc(String bucketCode);

    /** 按桶 code 查询未删除的 (前端编辑桶时只展示活的) */
    List<RiskTemplate> findByBucketCodeAndDeletedFalseOrderBySortOrderAscIdAsc(String bucketCode);

    /** 按桶 + agent 查询 (前端编辑智能体风险时使用) */
    List<RiskTemplate> findByBucketCodeAndAgentCodeOrderBySortOrderAsc(String bucketCode, String agentCode);
}
