package com.hex.projectgovern.module.wbs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BudgetSnapshotRepository extends JpaRepository<BudgetSnapshot, Long> {

    /** 项目最新一次快照(给项目详情页头部展示) */
    @Query("""
        SELECT s FROM BudgetSnapshot s
        WHERE s.projectId = :projectId
        ORDER BY s.snapshotDate DESC, s.id DESC
    """)
    List<BudgetSnapshot> findLatestByProject(@Param("projectId") Long projectId);

    /** 项目一段时间区间的所有快照(给趋势图用) */
    @Query("""
        SELECT s FROM BudgetSnapshot s
        WHERE s.projectId = :projectId
          AND s.snapshotDate BETWEEN :from AND :to
        ORDER BY s.snapshotDate ASC
    """)
    List<BudgetSnapshot> findByProjectIdAndDateRange(
            @Param("projectId") Long projectId,
            @Param("from") LocalDate from,
            @Param("to")   LocalDate to);

    /**
     * 项目最近 N 天的趋势 — 每天保留 1 条(取当日最新 id),按日期升序。
     * <p>用 native query: 同一天可能有多条(version 累加), 趋势图只关心"这一天的状态",
     * 留最新一条即可。
     * <p>不写 schema 前缀, 让 Hibernate 走当前默认 schema (PG=pmo, H2=PUBLIC)。
     */
    @Query(value = """
        SELECT s.* FROM budget_snapshot s
        WHERE s.project_id = :projectId
          AND s.id IN (
            SELECT MAX(s2.id) FROM budget_snapshot s2
            WHERE s2.project_id = :projectId
              AND s2.snapshot_date >= :since
            GROUP BY s2.snapshot_date
          )
        ORDER BY s.snapshot_date ASC
    """, nativeQuery = true)
    List<BudgetSnapshot> findTrendSince(@Param("projectId") Long projectId,
                                        @Param("since") LocalDate since);
}
