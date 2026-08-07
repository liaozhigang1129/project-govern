package com.hex.projectgovern.module.dingtalk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * V4.34: 抽取 upsertOne 到独立 Spring Bean.
 *
 * 关键修复 — 解决自调用 (self-invocation) 导致 {@code @Transactional(REQUIRES_NEW)} 失效的问题:
 *
 *   旧: DingTalkAttendanceSyncService.runSyncTransactional → this.upsertOne (绕过 AOP 代理, 事务注解丢失)
 *   新: DingTalkAttendanceSyncService.runSyncTransactional → upserter.upsertOne (走代理, 事务生效)
 *
 * 必须独立成 Bean 才能让 Spring AOP 拦截;在同一个类内直接调用, 注解不会触发代理, 报
 * jakarta.persistence.TransactionRequiredException: Executing an update/delete query
 *
 * 之前症状: dingtalk_attendance_sync_log 显示 status=SUCCESS + fetched=1858,
 * 但 created_count=0 / updated_count=0 (每条 upsert 都静默失败), 页面看不出"没同步"
 */
@Component
public class DingTalkAttendanceDailyUpserter {

    @Autowired
    private DingTalkAttendanceDailyRepository dailyRepo;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 获取走代理的 self (确保通过 Spring AOP 调用本类的 @Transactional 方法)
     * 用 ApplicationContext 拿一次代理引用即可
     */
    private DingTalkAttendanceDailyUpserter self() {
        return applicationContext.getBean(DingTalkAttendanceDailyUpserter.class);
    }

    /**
     * 单行 upsert, REQUIRES_NEW 事务.
     * 必须在 public 方法上, 跨 Bean 调用走代理, 事务才会真正生效.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int upsert(
            String userid, LocalDate workDate,
            Instant onDutyPlan, Instant onDutyActual, String onDutyResult, String onDutySource,
            String onDutyLocation, String onDutyLocationMethod, String onDutyLocationResult,
            Instant offDutyPlan, Instant offDutyActual, String offDutyResult, String offDutySource,
            String offDutyLocation, String offDutyLocationMethod, String offDutyLocationResult,
            Integer checkCount, Boolean isMakeup, Boolean isAbnormal, String abnormalTypes,
            String projectIds, String projectNames,
            Long pmoUserId, String userName, Long departmentId,
            String rawRecordIds, Instant dingtalkUpdatedAt, Instant syncedAt,
            Instant now, Integer workDuration) {
        return dailyRepo.upsertByUseridAndWorkDate(
                userid, workDate,
                onDutyPlan, onDutyActual, onDutyResult, onDutySource,
                onDutyLocation, onDutyLocationMethod, onDutyLocationResult,
                offDutyPlan, offDutyActual, offDutyResult, offDutySource,
                offDutyLocation, offDutyLocationMethod, offDutyLocationResult,
                checkCount, isMakeup, isAbnormal, abnormalTypes,
                projectIds, projectNames,
                pmoUserId, userName, departmentId,
                rawRecordIds, dingtalkUpdatedAt, syncedAt,
                workDuration, now);
    }

    /**
     * 对外暴露的统一入口: 走 self() 代理, 防止有人直接注入本类后绕过 self() 调用 upsert
     */
    public int upsertViaProxy(
            String userid, LocalDate workDate,
            Instant onDutyPlan, Instant onDutyActual, String onDutyResult, String onDutySource,
            String onDutyLocation, String onDutyLocationMethod, String onDutyLocationResult,
            Instant offDutyPlan, Instant offDutyActual, String offDutyResult, String offDutySource,
            String offDutyLocation, String offDutyLocationMethod, String offDutyLocationResult,
            Integer checkCount, Boolean isMakeup, Boolean isAbnormal, String abnormalTypes,
            String projectIds, String projectNames,
            Long pmoUserId, String userName, Long departmentId,
            String rawRecordIds, Instant dingtalkUpdatedAt, Instant syncedAt,
            Instant now, Integer workDuration) {
        return self().upsert(
                userid, workDate,
                onDutyPlan, onDutyActual, onDutyResult, onDutySource,
                onDutyLocation, onDutyLocationMethod, onDutyLocationResult,
                offDutyPlan, offDutyActual, offDutyResult, offDutySource,
                offDutyLocation, offDutyLocationMethod, offDutyLocationResult,
                checkCount, isMakeup, isAbnormal, abnormalTypes,
                projectIds, projectNames,
                pmoUserId, userName, departmentId,
                rawRecordIds, dingtalkUpdatedAt, syncedAt,
                now, workDuration);
    }
}
