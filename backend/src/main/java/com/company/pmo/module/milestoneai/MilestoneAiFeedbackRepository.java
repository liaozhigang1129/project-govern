package com.company.pmo.module.milestoneai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** P5-PM 反馈仓储 */
public interface MilestoneAiFeedbackRepository extends JpaRepository<MilestoneAiFeedback, Long> {

    Optional<MilestoneAiFeedback> findFirstByAdvisoryIdOrderByFeedbackAtDesc(Long advisoryId);

    List<MilestoneAiFeedback> findByAdvisoryIdOrderByFeedbackAtDesc(Long advisoryId);

    /** KPI: 近 N 天反馈汇总 (用 Map 避免 Projection) */
    @Query(value = """
        SELECT
            COUNT(DISTINCT advisory_id) AS feedback_count,
            SUM(CASE WHEN feedback_type='ACCEPTED' THEN 1 ELSE 0 END) AS accepted,
            SUM(CASE WHEN feedback_type='REJECTED' THEN 1 ELSE 0 END) AS rejected,
            SUM(CASE WHEN feedback_type='MISLEAD'  THEN 1 ELSE 0 END) AS misleads
        FROM milestone_ai_feedback
        WHERE feedback_at >= :since AND deleted = 0
    """, nativeQuery = true)
    List<Object[]> aggregateSince(@Param("since") Instant since);
}
