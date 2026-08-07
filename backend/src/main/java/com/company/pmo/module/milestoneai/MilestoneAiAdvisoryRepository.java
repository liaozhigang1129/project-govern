package com.company.pmo.module.milestoneai;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MilestoneAiAdvisoryRepository extends JpaRepository<MilestoneAiAdvisory, Long> {

    /** 项目下 PENDING 状态建议 (按 score 降序) */
    List<MilestoneAiAdvisory> findByProjectIdAndStatusAndDeletedFalseOrderByScoreDescIdAsc(
            Long projectId, String status);

    /** 项目下所有未软删建议, 按 score 降序 (分页) */
    Page<MilestoneAiAdvisory> findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(
            Long projectId, Pageable pageable);

    /** 严重度过滤 */
    List<MilestoneAiAdvisory> findByProjectIdInAndSeverityInAndStatusAndDeletedFalseOrderByScoreDescIdAsc(
            Collection<Long> projectIds, Collection<String> severities, String status);

    /** 单条 + 软删校验 */
    @Query("SELECT a FROM MilestoneAiAdvisory a WHERE a.id = :id AND a.deleted = false")
    Optional<MilestoneAiAdvisory> findActiveById(@Param("id") Long id);

    /** 幂等指纹查重 */
    Optional<MilestoneAiAdvisory> findFirstByFingerprintAndDeletedFalseOrderByIdDesc(String fingerprint);

    /** 统计: 项目下 PENDING 数 (按严重度) */
    @Query("SELECT a.severity, COUNT(a) FROM MilestoneAiAdvisory a " +
           "WHERE a.projectId = :projectId AND a.deleted = false AND a.status = 'PENDING' " +
           "GROUP BY a.severity")
    List<Object[]> countBySeverity(@Param("projectId") Long projectId);

    /** 批量跑: 拉所有未软删未应用的 active milestone (severity 倒序) */
    @Query("SELECT a FROM MilestoneAiAdvisory a " +
           "WHERE a.projectId IN :projectIds AND a.deleted = false AND a.status = 'PENDING' " +
           "ORDER BY a.score DESC, a.id ASC")
    List<MilestoneAiAdvisory> findPendingByProjectIds(@Param("projectIds") Collection<Long> projectIds);
}
