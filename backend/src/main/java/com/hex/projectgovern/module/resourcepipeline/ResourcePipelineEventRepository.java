package com.hex.projectgovern.module.resourcepipeline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ResourcePipelineEventRepository extends JpaRepository<ResourcePipelineEvent, Long> {

    @Query(value = "SELECT COUNT(DISTINCT user_id) FROM resource_pipeline_event " +
                   "WHERE to_status = :status AND start_date <= :today " +
                   "AND (end_date IS NULL OR end_date >= :today) AND deleted = false",
        nativeQuery = true)
    long countActiveByStatus(@Param("status") String status, @Param("today") LocalDate today);

    @Query(value = "SELECT COUNT(*) FROM (" +
                   "  SELECT user_id FROM resource_pipeline_event " +
                   "  WHERE start_date <= :today AND (end_date IS NULL OR end_date >= :today) " +
                   "  AND deleted = false " +
                   "  GROUP BY user_id HAVING SUM(allocation_pct) > 100" +
                   ") t",
        nativeQuery = true)
    long countOverloaded(@Param("today") LocalDate today);

    @Query(value = "SELECT COUNT(DISTINCT skill_code) FROM resource_skill WHERE deleted = false",
        nativeQuery = true)
    long countDistinctSkills();

    @Query(value = """
        SELECT COUNT(DISTINCT project_id) FROM resource_pipeline_event
        WHERE to_status = 'ALLOCATED' AND start_date <= CURRENT_DATE
          AND (end_date IS NULL OR end_date >= CURRENT_DATE) AND deleted = false
    """, nativeQuery = true)
    long countActiveProjects();

    @Query(value = """
        SELECT COALESCE(AVG(allocation_pct), 0) FROM resource_pipeline_event
        WHERE to_status = 'ALLOCATED' AND start_date <= CURRENT_DATE
          AND (end_date IS NULL OR end_date >= CURRENT_DATE) AND deleted = false
    """, nativeQuery = true)
    Double avgAllocation();

    @Query(value = """
        SELECT e.user_id, u.full_name, u.department_id, d.name,
               COALESCE(SUM(e.allocation_pct), 0) AS total_alloc,
               COUNT(DISTINCT e.project_id) AS proj_count
        FROM resource_pipeline_event e
        JOIN app_user u ON u.id = e.user_id
        LEFT JOIN department d ON d.id = u.department_id
        WHERE e.start_date <= :today
          AND (e.end_date IS NULL OR e.end_date >= :today)
          AND e.deleted = false
          AND u.deleted = false
        GROUP BY e.user_id, u.full_name, u.department_id, d.name
        HAVING SUM(e.allocation_pct) > 100
    """, nativeQuery = true)
    List<Object[]> findOverloadAlerts(@Param("today") LocalDate today);

    /**
     * 按 user × 周聚合 allocation_pct
     * PG: 周一为一周起点,date_trunc('week', ...) 在 PG 等价于 周一
     * MySQL: DATE_SUB(d, INTERVAL WEEKDAY(d) DAY) — 把日推到本周一
     * PG 版用 ISO 周一 (date_trunc 默认 monday)
     */
    @Query(value = """
        SELECT e.user_id,
               date_trunc('week', e.start_date)::date AS week,
               COALESCE(SUM(e.allocation_pct), 0) AS alloc_pct
        FROM resource_pipeline_event e
        WHERE e.start_date BETWEEN :from AND :to AND e.deleted = false
        GROUP BY e.user_id, date_trunc('week', e.start_date)
        ORDER BY e.user_id, week
    """, nativeQuery = true)
    List<Object[]> aggregateByUserAndWeek(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 部门产能 — 当下 active 事件按部门聚合
     * MySQL CURDATE() → PG CURRENT_DATE
     */
    @Query(value = """
        SELECT u.department_id, d.name, COUNT(DISTINCT e.user_id),
               COALESCE(AVG(e.allocation_pct), 0)
        FROM resource_pipeline_event e
        JOIN app_user u ON u.id = e.user_id
        LEFT JOIN department d ON d.id = u.department_id
        WHERE e.start_date <= CURRENT_DATE
          AND (e.end_date IS NULL OR e.end_date >= CURRENT_DATE)
          AND e.deleted = false
          AND u.deleted = false
        GROUP BY u.department_id, d.name
        ORDER BY 3 DESC
    """, nativeQuery = true)
    List<Object[]> aggregateByDepartment();
}