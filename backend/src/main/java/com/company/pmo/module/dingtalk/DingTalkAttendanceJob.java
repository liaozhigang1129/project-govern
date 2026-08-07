package com.company.pmo.module.dingtalk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 钉钉考勤定时同步 (V4.30)
 *
 * - 每周日 03:00 跑最近 N 天 (默认 14 = 2 周)
 * - cron 表达式由 system_config integration.dingtalk.attendance_cron 控制
 *   (注意: @Scheduled 在启动时锁定, 改完 cron 需重启)
 * - @ConditionalOnProperty false → 完全跳过, 不占资源
 */
@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "pmo.dingtalk.attendance-job-enabled", havingValue = "true", matchIfMissing = false)
public class DingTalkAttendanceJob {

    private final DingTalkAttendanceSyncService sync;
    private final DingTalkProperties props;

    @Scheduled(cron = "${pmo.dingtalk.attendance-cron:0 0 3 ? * SUN}", zone = "Asia/Shanghai")
    public void runWeekly() {
        log.info("[DingTalkAttendanceJob] 每周日同步开始");
        try {
            LocalDate today = LocalDate.now(ZoneId.systemDefault());
            LocalDate from = today.minusDays(props.getAttendanceWindowDays());
            DingTalkAttendanceSyncLog result = sync.syncNow("SCHEDULED", "SYSTEM", from, today);
            log.info("[DingTalkAttendanceJob] 跑批完成 status={} fetched={} created={} updated={} deleted={}",
                    result.getStatus(), result.getFetched(),
                    result.getCreatedCount(), result.getUpdatedCount(), result.getDeletedCount());
        } catch (Exception e) {
            log.error("[DingTalkAttendanceJob] 跑批失败", e);
        }
    }
}
