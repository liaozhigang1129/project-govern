package com.company.pmo.module.timesheet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TimesheetWeekRepository extends JpaRepository<TimesheetWeek, Long> {

    Optional<TimesheetWeek> findByIdAndDeletedFalse(Long id);

    Optional<TimesheetWeek> findByUserIdAndWeekStartAndDeletedFalse(Long userId, LocalDate weekStart);

    @Query("""
        SELECT t FROM TimesheetWeek t
        WHERE t.deleted = false
          AND (:userId  IS NULL OR t.userId   = :userId)
          AND (:status IS NULL OR t.status   = :status)
          AND (CAST(:from AS date) IS NULL OR t.weekStart >= :from)
          AND (CAST(:to   AS date) IS NULL OR t.weekStart <= :to)
        ORDER BY t.weekStart DESC, t.id DESC
        """)
    Page<TimesheetWeek> search(@Param("userId") Long userId,
                               @Param("status") TimesheetStatus status,
                               @Param("from") LocalDate from,
                               @Param("to")   LocalDate to,
                               Pageable pageable);

    /** 查用户在某区间的所有周报(用于负载聚合) */
    @Query("""
        SELECT t FROM TimesheetWeek t
        WHERE t.deleted = false
          AND t.userId  = :userId
          AND t.weekStart >= :from AND t.weekStart <= :to
        ORDER BY t.weekStart ASC
        """)
    List<TimesheetWeek> findUserRange(@Param("userId") Long userId,
                                      @Param("from") LocalDate from,
                                      @Param("to")   LocalDate to);

    /** 部门下所有用户的周报(PMO 视角) */
    @Query(value = """
        SELECT t.* FROM timesheet_week t
        JOIN app_user u ON u.id = t.user_id AND u.deleted = FALSE
        WHERE t.deleted = FALSE
          AND (:departmentId IS NULL OR u.department_id = :departmentId)
          AND t.week_start >= :from AND t.week_start <= :to
        ORDER BY t.week_start DESC, t.id DESC
        """, nativeQuery = true)
    List<TimesheetWeek> findDeptRange(@Param("departmentId") Long departmentId,
                                      @Param("from") LocalDate from,
                                      @Param("to")   LocalDate to);

    /**
     * P3-V2 催办:取某周已提交(submitted/approved)周报的所有 userId(去重)。
     * 不在结果集 = 本周尚未提交或没建草稿,会被催。
     */
    @Query("""
        SELECT DISTINCT t.userId FROM TimesheetWeek t
        WHERE t.deleted = false
          AND t.weekStart = :weekStart
          AND t.status IN (com.company.pmo.module.timesheet.TimesheetStatus.SUBMITTED,
                           com.company.pmo.module.timesheet.TimesheetStatus.APPROVED)
        """)
    List<Long> findSubmittedUserIdsForWeek(@Param("weekStart") LocalDate weekStart);
}
