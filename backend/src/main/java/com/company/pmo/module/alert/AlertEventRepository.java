package com.company.pmo.module.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

    /**
     * 找同一 (rule_id, target_id) 在过去 N 小时内是否已有未解决事件 (防重复触发)
     */
    @Query("""
        SELECT e FROM AlertEvent e
        WHERE e.ruleId = :ruleId
          AND e.targetId = :targetId
          AND e.status IN ('NEW', 'ACKNOWLEDGED')
          AND e.triggeredAt > :since
          AND e.deleted = false
        """)
    List<AlertEvent> findRecentOpen(
            @Param("ruleId") Long ruleId,
            @Param("targetId") Long targetId,
            @Param("since") OffsetDateTime since);

    @Query("""
        SELECT e FROM AlertEvent e
        WHERE e.status = 'NEW'
          AND e.deleted = false
        ORDER BY e.severity DESC, e.triggeredAt DESC
        """)
    List<AlertEvent> findAllNew();

    @Query("""
        SELECT e FROM AlertEvent e
        WHERE e.notifyStatus = 'PENDING'
          AND e.deleted = false
        """)
    List<AlertEvent> findPendingNotify();
}
