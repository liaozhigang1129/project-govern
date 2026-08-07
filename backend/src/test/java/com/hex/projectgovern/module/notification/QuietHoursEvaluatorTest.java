package com.hex.projectgovern.module.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * QuietHoursEvaluator 单元测试(P2 #2)。
 *
 * 覆盖:
 *  - matches() 跨午夜 / 同日 / 边界值 / 单点 / 解析失败
 *  - isInQuietHours() 命中/未命中/无窗口/空用户
 *  - 失败安全:repo 抛错不阻断(返回 false)
 */
class QuietHoursEvaluatorTest {

    // ===== matches() 跨午夜/同日 判定 =====

    @Test
    @DisplayName("matches: 同日窗口 12:00 ~ 14:00, 13:00 → true")
    void matches_sameDay_inside() {
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(13, 0), stubRepo(List.of()));
        assertThat(e.matches(LocalTime.of(13, 0), "12:00", "14:00")).isTrue();
    }

    @Test
    @DisplayName("matches: 同日窗口 12:00 ~ 14:00, 11:59 → false")
    void matches_sameDay_before() {
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(11, 59), stubRepo(List.of()));
        assertThat(e.matches(LocalTime.of(11, 59), "12:00", "14:00")).isFalse();
    }

    @Test
    @DisplayName("matches: 同日窗口 12:00 ~ 14:00, 14:01 → false")
    void matches_sameDay_after() {
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(14, 1), stubRepo(List.of()));
        assertThat(e.matches(LocalTime.of(14, 1), "12:00", "14:00")).isFalse();
    }

    @Test
    @DisplayName("matches: 同日窗口 12:00 ~ 14:00, 边界 12:00 / 14:00 → true(闭区间)")
    void matches_sameDay_boundary() {
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(0, 0), stubRepo(List.of()));
        assertThat(e.matches(LocalTime.of(12, 0), "12:00", "14:00")).isTrue();
        assertThat(e.matches(LocalTime.of(14, 0), "12:00", "14:00")).isTrue();
    }

    @Test
    @DisplayName("matches: 跨午夜 22:00 ~ 08:00, 23:00 → true")
    void matches_crossNight_evening() {
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(23, 0), stubRepo(List.of()));
        assertThat(e.matches(LocalTime.of(23, 0), "22:00", "08:00")).isTrue();
    }

    @Test
    @DisplayName("matches: 跨午夜 22:00 ~ 08:00, 02:00 → true")
    void matches_crossNight_morning() {
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(2, 0), stubRepo(List.of()));
        assertThat(e.matches(LocalTime.of(2, 0), "22:00", "08:00")).isTrue();
    }

    @Test
    @DisplayName("matches: 跨午夜 22:00 ~ 08:00, 边界 22:00 / 08:00 → true(闭区间)")
    void matches_crossNight_boundary() {
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(0, 0), stubRepo(List.of()));
        assertThat(e.matches(LocalTime.of(22, 0), "22:00", "08:00")).isTrue();
        assertThat(e.matches(LocalTime.of(8, 0), "22:00", "08:00")).isTrue();
    }

    @Test
    @DisplayName("matches: 跨午夜 22:00 ~ 08:00, 10:00 → false")
    void matches_crossNight_outside() {
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(10, 0), stubRepo(List.of()));
        assertThat(e.matches(LocalTime.of(10, 0), "22:00", "08:00")).isFalse();
    }

    @Test
    @DisplayName("matches: start == end → 视为无效,永 false")
    void matches_samePoint() {
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(0, 0), stubRepo(List.of()));
        assertThat(e.matches(LocalTime.of(12, 0), "12:00", "12:00")).isFalse();
    }

    @Test
    @DisplayName("matches: 时间字符串解析失败 → false")
    void matches_invalidInput() {
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(0, 0), stubRepo(List.of()));
        assertThat(e.matches(LocalTime.of(12, 0), null, "14:00")).isFalse();
        assertThat(e.matches(LocalTime.of(12, 0), "12:00", null)).isFalse();
        assertThat(e.matches(LocalTime.of(12, 0), "abc", "14:00")).isFalse();
        assertThat(e.matches(LocalTime.of(12, 0), "25:00", "14:00")).isFalse();
    }

    @Test
    @DisplayName("matches: 短格式 '9:00' 自动补零为 '09:00'")
    void matches_shortFormatPadded() {
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(0, 0), stubRepo(List.of()));
        assertThat(e.matches(LocalTime.of(9, 30), "9:00", "10:00")).isTrue();
    }

    // ===== isInQuietHours() 全链路 =====

    @Test
    @DisplayName("isInQuietHours: 用户无窗口 → false")
    void isQuiet_noWindows() {
        UserImQuietHoursRepository repo = stubRepo(List.of());
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(23, 0), repo);
        assertThat(e.isInQuietHours(7L)).isFalse();
    }

    @Test
    @DisplayName("isInQuietHours: 命中跨午夜窗口(23:00 in 22:00~08:00) → true")
    void isQuiet_crossNightHit() {
        UserImQuietHours w = windowOf(7L, "22:00", "08:00", true);
        UserImQuietHoursRepository repo = stubRepo(List.of(w));
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(23, 0), repo);
        assertThat(e.isInQuietHours(7L)).isTrue();
    }

    @Test
    @DisplayName("isInQuietHours: 多窗口 OR,任一命中即 true")
    void isQuiet_multiWindowAnyMatch() {
        UserImQuietHours w1 = windowOf(7L, "12:00", "13:00", true);   // 午餐
        UserImQuietHours w2 = windowOf(7L, "22:00", "08:00", true);   // 深夜
        UserImQuietHoursRepository repo = stubRepo(List.of(w1, w2));
        // 当前 23:00 → 命中 w2
        assertThat(new QuietHoursEvaluator(clockAt(23, 0), repo).isInQuietHours(7L)).isTrue();
        // 当前 12:30 → 命中 w1
        assertThat(new QuietHoursEvaluator(clockAt(12, 30), repo).isInQuietHours(7L)).isTrue();
        // 当前 10:00 → 都不命中
        assertThat(new QuietHoursEvaluator(clockAt(10, 0), repo).isInQuietHours(7L)).isFalse();
    }

    @Test
    @DisplayName("isInQuietHours: userId 为 null → false")
    void isQuiet_nullUser() {
        UserImQuietHoursRepository repo = stubRepo(List.of());
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(0, 0), repo);
        assertThat(e.isInQuietHours(null)).isFalse();
    }

    @Test
    @DisplayName("isInQuietHours: repo 抛错 → 失败安全,返 false")
    void isQuiet_repoFails() {
        UserImQuietHoursRepository repo = mock(UserImQuietHoursRepository.class);
        when(repo.findByUserIdAndEnabledTrue(7L))
                .thenThrow(new RuntimeException("db down"));
        QuietHoursEvaluator e = new QuietHoursEvaluator(clockAt(0, 0), repo);
        assertThat(e.isInQuietHours(7L)).isFalse();
    }

    // ===== 辅助 =====

    private static Clock clockAt(int hour, int minute) {
        return Clock.fixed(
                java.time.LocalDateTime.of(2025, 1, 15, hour, minute)
                        .atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
    }

    /** Mockito 桩 — 只 stub findByUserIdAndEnabledTrue,其他方法返默认值 */
    private static UserImQuietHoursRepository stubRepo(List<UserImQuietHours> enabledWindows) {
        UserImQuietHoursRepository repo = mock(UserImQuietHoursRepository.class);
        when(repo.findByUserIdAndEnabledTrue(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(enabledWindows);
        return repo;
    }

    private static UserImQuietHours windowOf(Long userId, String start, String end, boolean enabled) {
        return UserImQuietHours.builder()
                .userId(userId).startTime(start).endTime(end)
                .timezone("Asia/Shanghai").enabled(enabled).build();
    }
}
