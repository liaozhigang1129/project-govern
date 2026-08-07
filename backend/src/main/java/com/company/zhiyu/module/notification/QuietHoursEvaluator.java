package com.company.zhiyu.module.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * DND 时段判定器(P2 #2)。
 *
 * 设计:
 *  - 无依赖、可单测(Clock 注入)
 *  - 跨午夜支持:end &lt; start 视为跨越 00:00(例 22:00 ~ 08:00)
 *  - 多窗口 OR:任何窗口命中即视为 DND
 *  - 窗口数据源 = UserImQuietHoursRepository.findByUserIdAndEnabledTrue(userId)
 *  - 失败安全:任何异常返回 false(不阻断通知)
 *
 * 调用方:
 *  - NotificationDispatcher.sendToIm() 在拼出 perUser 消息后,发送前调用 isInQuietHours(userId)
 */
@Component
@Slf4j
public class QuietHoursEvaluator {

    private final Clock clock;
    private final UserImQuietHoursRepository repo;

    public QuietHoursEvaluator(Clock clock, UserImQuietHoursRepository repo) {
        this.clock = clock;
        this.repo = repo;
    }

    /**
     * 当前是否在该用户的 DND 时段内。
     */
    public boolean isInQuietHours(Long userId) {
        if (userId == null) return false;
        List<UserImQuietHours> windows;
        try {
            windows = repo.findByUserIdAndEnabledTrue(userId);
        } catch (Exception ex) {
            log.warn("[DND] repo failed for userId={}, fail-open: {}", userId, ex.getMessage());
            return false;
        }
        if (windows == null || windows.isEmpty()) return false;

        // MVP: 全部按系统时区算; 后续可按 window.timezone 切换
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.systemDefault());
        LocalTime nowT = now.toLocalTime();

        for (UserImQuietHours w : windows) {
            if (matches(nowT, w.getStartTime(), w.getEndTime())) {
                log.debug("[DND] userId={} in quiet window {}-{}",
                        userId, w.getStartTime(), w.getEndTime());
                return true;
            }
        }
        return false;
    }

    /**
     * 判定 now 是否在 [start, end] 闭区间内(支持跨午夜)。
     * HH:mm 解析失败时返回 false(单点异常不影响整体)。
     */
    boolean matches(LocalTime now, String start, String end) {
        if (start == null || end == null) return false;
        LocalTime s, e;
        try {
            s = LocalTime.parse(pad(start));
            e = LocalTime.parse(pad(end));
        } catch (Exception ex) {
            return false;
        }
        if (s.equals(e)) {
            // start == end → 视为整点单点;MVP 不支持,按"不在 DND"处理
            return false;
        }
        if (s.isBefore(e)) {
            // 同日: [s, e]
            return !now.isBefore(s) && !now.isAfter(e);
        } else {
            // 跨午夜: [s, 23:59:59] ∪ [00:00, e]
            return !now.isBefore(s) || !now.isAfter(e);
        }
    }

    private String pad(String t) {
        // "9:00" → "09:00"
        String[] p = t.split(":");
        if (p.length != 2) return t;
        return String.format("%02d:%02d", Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }
}
