package com.company.pmo.module.dingtalk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * V4.33 钉钉考勤每日聚合 Repository
 *
 * 一行 = 一个 userid 一个 work_date (V4.33 唯一键 uq_daily_user_date)
 * 同步走 upsertByUseridAndWorkDate (native INSERT ... ON DUPLICATE KEY UPDATE)
 * 列表/详情走 Spring Data 派生方法 + JPQL
 */
public interface DingTalkAttendanceDailyRepository
        extends JpaRepository<DingTalkAttendanceDaily, Long>, JpaSpecificationExecutor<DingTalkAttendanceDaily> {

    /**
     * 找当天聚合行 (upsert 前查询用)
     */
    Optional<DingTalkAttendanceDaily> findByUseridAndWorkDateAndDeletedFalse(String userid, LocalDate workDate);

    /**
     * 找某用户某段时间的所有日聚合行
     */
    List<DingTalkAttendanceDaily> findByUseridAndWorkDateBetweenAndDeletedFalseOrderByWorkDateDesc(
            String userid, LocalDate from, LocalDate to);

    /**
     * V4.34: 按 pmoUserId + workDate 区间拉 (自动填报名用)
     *  join fetch 不会做,但 dailyRepo 也不太大, 7 天范围一次拉够
     */
    @Query("""
        SELECT d FROM DingTalkAttendanceDaily d
        WHERE d.pmoUserId = :pmoUserId
          AND d.deleted = false
          AND d.workDate BETWEEN :from AND :to
        ORDER BY d.workDate ASC
        """)
    List<DingTalkAttendanceDaily> findByPmoUserIdAndRange(
            @Param("pmoUserId") Long pmoUserId,
            @Param("from") LocalDate from,
            @Param("to")   LocalDate to);

    /**
     * 列表分页 (按日期倒序, 后续会用 Specification 接过滤)
     */
    @Query("SELECT d FROM DingTalkAttendanceDaily d WHERE d.deleted = false ORDER BY d.workDate DESC, d.userid ASC")
    Page<DingTalkAttendanceDaily> findAllActive(Pageable pageable);

    /**
     * V4.33+ 列表筛选 (dateFrom/dateTo/useridKeyword/isAbnormal)
     * 全部可选, null/空字符串 = 不过滤
     * kw 模糊匹配 userid 或 userName (LIKE %kw%)
     */
    @Query("""
        SELECT d FROM DingTalkAttendanceDaily d
        WHERE d.deleted = false
          AND (:dateFrom IS NULL OR d.workDate >= :dateFrom)
          AND (:dateTo IS NULL OR d.workDate <= :dateTo)
          AND (:kw = '' OR LOWER(d.userid) LIKE LOWER(CONCAT('%', :kw, '%'))
                          OR LOWER(d.userName) LIKE LOWER(CONCAT('%', :kw, '%')))
          AND (:isAbnormal IS NULL OR d.isAbnormal = :isAbnormal)
        ORDER BY d.workDate DESC, d.userid ASC
        """)
    Page<DingTalkAttendanceDaily> findWithFilters(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("kw") String kw,
            @Param("isAbnormal") Boolean isAbnormal,
            Pageable pageable);

    /**
     * 区间内总数 (统计用)
     */
    @Query("SELECT COUNT(d) FROM DingTalkAttendanceDaily d WHERE d.deleted = false")
    long countActive();

    @Query("SELECT COUNT(d) FROM DingTalkAttendanceDaily d WHERE d.deleted = false AND d.workDate BETWEEN ?1 AND ?2")
    long countByDateRange(LocalDate from, LocalDate to);

    @Query("SELECT COUNT(d) FROM DingTalkAttendanceDaily d WHERE d.deleted = false AND d.isAbnormal = true AND d.workDate BETWEEN ?1 AND ?2")
    long countAbnormalByDateRange(LocalDate from, LocalDate to);

    // ============================================================
    // Upsert: native ON DUPLICATE KEY UPDATE
    // 用于同步 service, 严格按 (userid, work_date) 去重
    // ============================================================
    @Modifying
    @Query(value = """
            INSERT INTO dingtalk_attendance_daily (
                userid, work_date,
                on_duty_plan, on_duty_actual, on_duty_result, on_duty_source,
                on_duty_location, on_duty_location_method, on_duty_location_result,
                off_duty_plan, off_duty_actual, off_duty_result, off_duty_source,
                off_duty_location, off_duty_location_method, off_duty_location_result,
                check_count, is_makeup, is_abnormal, abnormal_types,
                project_ids, project_names,
                pmo_user_id, user_name, department_id,
                raw_record_ids, dingtalk_updated_at, synced_at,
                deleted, created_at, updated_at,
                work_duration
            ) VALUES (
                :userid, :workDate,
                :onDutyPlan, :onDutyActual, :onDutyResult, :onDutySource,
                :onDutyLocation, :onDutyLocationMethod, :onDutyLocationResult,
                :offDutyPlan, :offDutyActual, :offDutyResult, :offDutySource,
                :offDutyLocation, :offDutyLocationMethod, :offDutyLocationResult,
                :checkCount, :isMakeup, :isAbnormal, :abnormalTypes,
                :projectIds, :projectNames,
                :pmoUserId, :userName, :departmentId,
                :rawRecordIds, :dingtalkUpdatedAt, :syncedAt,
                0, :now, :now,
                :workDuration
            )
            ON DUPLICATE KEY UPDATE
                on_duty_plan           = VALUES(on_duty_plan),
                on_duty_actual         = VALUES(on_duty_actual),
                on_duty_result         = VALUES(on_duty_result),
                on_duty_source         = VALUES(on_duty_source),
                on_duty_location       = VALUES(on_duty_location),
                on_duty_location_method= VALUES(on_duty_location_method),
                on_duty_location_result= VALUES(on_duty_location_result),
                off_duty_plan          = VALUES(off_duty_plan),
                off_duty_actual        = VALUES(off_duty_actual),
                off_duty_result        = VALUES(off_duty_result),
                off_duty_source        = VALUES(off_duty_source),
                off_duty_location      = VALUES(off_duty_location),
                off_duty_location_method=VALUES(off_duty_location_method),
                off_duty_location_result=VALUES(off_duty_location_result),
                check_count            = VALUES(check_count),
                is_makeup              = VALUES(is_makeup),
                is_abnormal            = VALUES(is_abnormal),
                abnormal_types         = VALUES(abnormal_types),
                project_ids            = VALUES(project_ids),
                project_names          = VALUES(project_names),
                pmo_user_id            = VALUES(pmo_user_id),
                user_name              = VALUES(user_name),
                department_id          = VALUES(department_id),
                raw_record_ids         = VALUES(raw_record_ids),
                dingtalk_updated_at    = VALUES(dingtalk_updated_at),
                synced_at              = VALUES(synced_at),
                work_duration          = VALUES(work_duration),
                updated_at             = :now
            """, nativeQuery = true)
    int upsertByUseridAndWorkDate(
            @Param("userid") String userid,
            @Param("workDate") LocalDate workDate,
            @Param("onDutyPlan") Instant onDutyPlan,
            @Param("onDutyActual") Instant onDutyActual,
            @Param("onDutyResult") String onDutyResult,
            @Param("onDutySource") String onDutySource,
            @Param("onDutyLocation") String onDutyLocation,
            @Param("onDutyLocationMethod") String onDutyLocationMethod,
            @Param("onDutyLocationResult") String onDutyLocationResult,
            @Param("offDutyPlan") Instant offDutyPlan,
            @Param("offDutyActual") Instant offDutyActual,
            @Param("offDutyResult") String offDutyResult,
            @Param("offDutySource") String offDutySource,
            @Param("offDutyLocation") String offDutyLocation,
            @Param("offDutyLocationMethod") String offDutyLocationMethod,
            @Param("offDutyLocationResult") String offDutyLocationResult,
            @Param("checkCount") Integer checkCount,
            @Param("isMakeup") Boolean isMakeup,
            @Param("isAbnormal") Boolean isAbnormal,
            @Param("abnormalTypes") String abnormalTypes,
            @Param("projectIds") String projectIds,
            @Param("projectNames") String projectNames,
            @Param("pmoUserId") Long pmoUserId,
            @Param("userName") String userName,
            @Param("departmentId") Long departmentId,
            @Param("rawRecordIds") String rawRecordIds,
            @Param("dingtalkUpdatedAt") Instant dingtalkUpdatedAt,
            @Param("syncedAt") Instant syncedAt,
            @Param("workDuration") Integer workDuration,
            @Param("now") Instant now);
}
