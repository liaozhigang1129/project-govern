package com.hex.projectgovern.module.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * 多条件分页查询 (T-01 AlertController)
     * - typeCode 可选 (按规则类型)
     * - severity 可选 (HIGH / MEDIUM / LOW / CRITICAL)
     * - status 可选 (NEW / ACKNOWLEDGED / RESOLVED / SUPPRESSED)
     * - projectId 可选
     */
    @Query("""
        SELECT e FROM AlertEvent e
        WHERE e.deleted = false
          AND (:typeCode IS NULL OR e.ruleId IN (
                SELECT r.id FROM AlertRule r WHERE r.typeCode = :typeCode))
          AND (:severity IS NULL OR e.severity = :severity)
          AND (:status   IS NULL OR e.status = :status)
          AND (:projectId IS NULL OR e.projectId = :projectId)
        ORDER BY e.severity DESC, e.triggeredAt DESC
    """)
    Page<AlertEvent> search(
            @Param("typeCode") String typeCode,
            @Param("severity") String severity,
            @Param("status") String status,
            @Param("projectId") Long projectId,
            Pageable pageable);

    /** 按 severity 分组统计 NEW 事件数 (T-01 stats) */
    @Query("""
        SELECT e.severity, COUNT(e) FROM AlertEvent e
        WHERE e.deleted = false AND e.status = 'NEW'
        GROUP BY e.severity
    """)
    List<Object[]> countBySeverityNew();

    /** 按 typeCode 分组统计 NEW 事件数 (T-01 stats) */
    @Query("""
        SELECT r.typeCode, COUNT(e) FROM AlertEvent e, AlertRule r
        WHERE e.deleted = false AND e.status = 'NEW' AND e.ruleId = r.id
        GROUP BY r.typeCode
    """)
    List<Object[]> countNewByTypeCode();

    /** 按 (ruleId, targetId) 找最近一条 (调度幂等用) */
    @Query("""
        SELECT e FROM AlertEvent e
        WHERE e.ruleId = :ruleId
          AND e.targetId = :targetId
          AND e.deleted = false
        ORDER BY e.triggeredAt DESC
    """)
    List<AlertEvent> findByRuleAndTarget(
            @Param("ruleId") Long ruleId,
            @Param("targetId") Long targetId,
            Pageable pageable);
}
