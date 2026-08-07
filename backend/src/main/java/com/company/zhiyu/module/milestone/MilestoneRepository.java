package com.company.zhiyu.module.milestone;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findByProjectIdAndDeletedFalseOrderBySequence(Long projectId);
    /** 一次性 JOIN FETCH status,避免 LAZY 加载;走所有项目 ID 的 IN 查询 */
    @Query("SELECT m FROM Milestone m JOIN FETCH m.status WHERE m.projectId IN :projectIds AND m.deleted = false ORDER BY m.projectId ASC, m.sequence ASC")
    List<Milestone> findByProjectIdWithStatus(@org.springframework.data.repository.query.Param("projectIds") java.util.Collection<Long> projectIds);

    List<Milestone> findByProjectIdInOrderByProjectIdAscSequenceAsc(java.util.List<Long> projectIds);

    /**
     * 同上,但会一次性 JOIN 拉取 status,避免 Controller 序列化时触发 LAZY 加载。
     * listByProject 失败时 (e.g. controller 序列化阶段) 退化到此方法。
     */
    @Query("SELECT m FROM Milestone m JOIN FETCH m.status JOIN FETCH m.phase WHERE m.projectId = :projectId AND m.deleted = false ORDER BY m.phase.sortOrder ASC, m.sequence ASC")
    List<Milestone> findByProjectIdWithStatus(@Param("projectId") Long projectId);
    long countByProjectIdAndDeletedFalse(Long projectId);
    long countByProjectIdAndStatusCodeAndDeletedFalse(Long projectId, String statusCode);

    /**
     * 计算某个项目的加权进度 (0-100)。
     *
     * 公式: SUM(weight WHERE status=COMPLETED) / SUM(weight) * 100
     * 没有里程碑时返回 0,所有 weight=0 时也返回 0,避免除零。
     *
     * 用 JPQL 聚合 + 一次往返,避免 LAZY 加载 status + 一次 findAll status。
     */
    @Query("""
        SELECT COALESCE(
            ROUND(
                100.0 * SUM(CASE WHEN s.code = 'COMPLETED' THEN m.weight ELSE 0 END) /
                NULLIF(SUM(m.weight), 0)
            ), 0)
        FROM Milestone m JOIN m.status s
        WHERE m.projectId = :projectId AND m.deleted = false
    """)
    int computeWeightedProgressPct(@Param("projectId") Long projectId);
}
