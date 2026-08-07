package com.hex.projectgovern.module.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RiskRepository extends JpaRepository<Risk, Long> {

    /** 拉项目所有未软删风险, 按 score 降序(高风险在前) */
    List<Risk> findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(Long projectId);

    /** 单条带项目校验 */
    @Query("SELECT r FROM Risk r WHERE r.id = :id AND r.deleted = false")
    Optional<Risk> findActiveById(@Param("id") Long id);

    /** 检查 code 唯一性 (新建时) */
    long countByProjectIdAndCodeAndDeletedFalse(Long projectId, String code);

    /** 检查 code 唯一性 (更新时排除自己) */
    @Query("SELECT COUNT(r) FROM Risk r WHERE r.projectId = :projectId AND r.code = :code AND r.deleted = false AND r.id <> :excludeId")
    long countByProjectIdAndCodeExcludingId(@Param("projectId") Long projectId,
                                             @Param("code") String code,
                                             @Param("excludeId") Long excludeId);

    /** 活跃风险 (状态非 CLOSED/ACCEPTED) */
    @Query("SELECT r FROM Risk r WHERE r.projectId = :projectId AND r.deleted = false " +
           "AND r.status NOT IN ('CLOSED','ACCEPTED') ORDER BY r.score DESC, r.id ASC")
    List<Risk> findActiveByProject(@Param("projectId") Long projectId);

    /**
     * P4 KPI 聚合 (H2 兼容: 返回 {@code Object[]}, 0=total, 1=active, 2=critical, 3=high, 4=occurred, 5=maxScore)
     * <p>PG 返回单行 Object[], H2 也一样。但 H2 的 MAX(score) 是 Long, 不是 Integer。
     */
    @Query("""
        SELECT COUNT(r),
               COUNT(CASE WHEN r.status NOT IN ('CLOSED','ACCEPTED') THEN 1 ELSE NULL END),
               COUNT(CASE WHEN r.level = 'CRITICAL' AND r.status NOT IN ('CLOSED','ACCEPTED') THEN 1 ELSE NULL END),
               COUNT(CASE WHEN r.level = 'HIGH'     AND r.status NOT IN ('CLOSED','ACCEPTED') THEN 1 ELSE NULL END),
               COUNT(CASE WHEN r.status = 'OCCURRED' THEN 1 ELSE NULL END),
               COALESCE(MAX(CASE WHEN r.status NOT IN ('CLOSED','ACCEPTED') THEN r.score ELSE NULL END), 0)
        FROM Risk r
        WHERE r.projectId = :projectId AND r.deleted = false
    """)
    Object[] aggregateHealth(@Param("projectId") Long projectId);
}
