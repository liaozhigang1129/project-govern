package com.company.pmo.module.dingtalk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DingTalkLeaveRepository.findCovering 行为测试 (V4.34 工时自动填充前置)
 *
 * <h3>被测 SQL 谓词</h3>
 * <pre>
 *   userid = :userid
 *   AND deleted = false
 *   AND status  = 'NORMAL'
 *   AND start_time &lt; :dayEnd
 *   AND (end_time IS NULL OR end_time &gt; :dayStart)
 * </pre>
 *
 * <h3>测试策略</h3>
 * 1 个 userid (dt-001) + 多种请假/边界条件, 验证:
 * <ul>
 *   <li>命中: 请假覆盖当日, 含 [startDay, endDay] / [startDay, ∞) / (-∞, endDay]</li>
 *   <li>不命中: 请假完全在 [dayStart, dayEnd) 之外 (前一日结束 / 后一日开始)</li>
 *   <li>不命中: deleted=true / status≠NORMAL</li>
 *   <li>排序: start_time ASC</li>
 *   <li>userid 隔离: 别人的请假不返回</li>
 * </ul>
 *
 * <h3>关于时区</h3>
 * 为了避免测试结果依赖运行机器时区, 这里统一用 UTC Instant, dayStart = 当天 00:00 UTC, dayEnd = 次日 00:00 UTC.
 * 业务上 TimesheetAutoFillService 会按系统时区计算窗口, 此处只验证 SQL 谓词.
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
class DingTalkLeaveRepositoryTest {

    @Autowired DingTalkLeaveRepository leaveRepository;

    private static final String USER = "dt-001";
    private static final String OTHER_USER = "dt-999";

    @BeforeEach
    void seed() {
        leaveRepository.deleteAll();
    }

    // ============================================================
    // helper
    // ============================================================

    private DingTalkLeave mkLeave(String userid, String leaveId, String status, boolean deleted,
                                  Instant start, Instant end) {
        DingTalkLeave l = new DingTalkLeave();
        l.setUserid(userid);
        l.setLeaveId(leaveId);
        l.setLeaveType("事假");
        l.setStatus(status);
        l.setDeleted(deleted);
        l.setStartTime(start);
        l.setEndTime(end);
        l.setDuration(java.math.BigDecimal.valueOf(4));
        l.setSyncedAt(Instant.now());
        l.setCreatedAt(Instant.now());
        l.setUpdatedAt(Instant.now());
        return l;
    }

    /** 当天 00:00 UTC 到 次日 00:00 UTC */
    private static Instant[] utcDayWindow(String isoDay) {
        LocalDate d = LocalDate.parse(isoDay);
        return new Instant[] { d.atStartOfDay(ZoneOffset.UTC).toInstant(),
                                d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() };
    }

    // ============================================================
    // 1) 跨日覆盖: start<dayStart, end>dayEnd (整天请假)
    // ============================================================
    @Test
    @DisplayName("整天请假 (start=前日 09:00, end=后日 18:00) → 命中")
    void covering_entireDay_hit() {
        Instant[] win = utcDayWindow("2025-06-15");
        leaveRepository.save(mkLeave(USER, "L001", "NORMAL", false,
                Instant.parse("2025-06-14T09:00:00Z"),
                Instant.parse("2025-06-16T18:00:00Z")));

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).getLeaveId()).isEqualTo("L001");
    }

    // ============================================================
    // 2) start 当日 00:00 (边界: 等于 dayStart) → 仍命中 (end > dayStart)
    // ============================================================
    @Test
    @DisplayName("start = dayStart 边界: 仍命中 (闭区间一端等号)")
    void covering_startEqualsDayStart_hit() {
        Instant[] win = utcDayWindow("2025-06-15");
        leaveRepository.save(mkLeave(USER, "L002", "NORMAL", false,
                win[0],                                                // start = dayStart
                Instant.parse("2025-06-15T09:00:00Z")));

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).hasSize(1);
    }

    // ============================================================
    // 3) end 当日 00:00 (边界: 等于 dayStart, 不 > dayStart) → 不命中
    // ============================================================
    @Test
    @DisplayName("end = dayStart 边界: 不命中 (闭区间另一端开)")
    void covering_endEqualsDayStart_miss() {
        Instant[] win = utcDayWindow("2025-06-15");
        leaveRepository.save(mkLeave(USER, "L003", "NORMAL", false,
                Instant.parse("2025-06-14T08:00:00Z"),
                win[0]));                                              // end = dayStart

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).isEmpty();
    }

    // ============================================================
    // 4) start = dayEnd (边界) → 不命中
    // ============================================================
    @Test
    @DisplayName("start = dayEnd 边界: 不命中 (闭区间另一端开)")
    void covering_startEqualsDayEnd_miss() {
        Instant[] win = utcDayWindow("2025-06-15");
        leaveRepository.save(mkLeave(USER, "L004", "NORMAL", false,
                win[1],                                                // start = dayEnd
                Instant.parse("2025-06-15T20:00:00Z")));

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).isEmpty();
    }

    // ============================================================
    // 5) end_time IS NULL (加班/补卡场景) → 命中 (只要 start < dayEnd)
    // ============================================================
    @Test
    @DisplayName("end_time IS NULL (加班): start < dayEnd → 命中")
    void covering_endNull_overtime_hit() {
        Instant[] win = utcDayWindow("2025-06-15");
        leaveRepository.save(mkLeave(USER, "L005", "NORMAL", false,
                Instant.parse("2025-06-15T18:30:00Z"),
                null));                                                // 加班无 end

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).hasSize(1);
    }

    // ============================================================
    // 6) end_time IS NULL + start >= dayEnd → 不命中
    // ============================================================
    @Test
    @DisplayName("end_time IS NULL + start >= dayEnd: 不命中")
    void covering_endNull_startFuture_miss() {
        Instant[] win = utcDayWindow("2025-06-15");
        // dayEnd = 2025-06-16T00:00:00Z. start = dayEnd + 1s
        leaveRepository.save(mkLeave(USER, "L006", "NORMAL", false,
                win[1].plusSeconds(1),
                null));

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).isEmpty();
    }

    // ============================================================
    // 7) 完全在 dayStart 之前结束 → 不命中
    // ============================================================
    @Test
    @DisplayName("end < dayStart: 不命中 (昨日结束)")
    void covering_endedYesterday_miss() {
        Instant[] win = utcDayWindow("2025-06-15");
        leaveRepository.save(mkLeave(USER, "L007", "NORMAL", false,
                Instant.parse("2025-06-14T08:00:00Z"),
                Instant.parse("2025-06-14T18:00:00Z")));

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).isEmpty();
    }

    // ============================================================
    // 8) 完全在 dayEnd 之后开始 → 不命中
    // ============================================================
    @Test
    @DisplayName("start >= dayEnd: 不命中 (明日开始)")
    void covering_startsTomorrow_miss() {
        Instant[] win = utcDayWindow("2025-06-15");
        leaveRepository.save(mkLeave(USER, "L008", "NORMAL", false,
                Instant.parse("2025-06-16T08:00:00Z"),
                Instant.parse("2025-06-16T18:00:00Z")));

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).isEmpty();
    }

    // ============================================================
    // 9) deleted=true → 不命中
    // ============================================================
    @Test
    @DisplayName("deleted=true: 不��中 (软删除过滤)")
    void covering_deleted_miss() {
        Instant[] win = utcDayWindow("2025-06-15");
        leaveRepository.save(mkLeave(USER, "L009", "NORMAL", true,  // deleted
                Instant.parse("2025-06-14T08:00:00Z"),
                Instant.parse("2025-06-16T18:00:00Z")));

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).isEmpty();
    }

    // ============================================================
    // 10) status = 'REJECT' / 'REVOKE' → 不命中
    // ============================================================
    @Test
    @DisplayName("status = 'REJECT': 不命中 (非 NORMAL 状态过滤)")
    void covering_rejected_miss() {
        Instant[] win = utcDayWindow("2025-06-15");
        leaveRepository.save(mkLeave(USER, "L010A", "REJECT", false,
                Instant.parse("2025-06-14T08:00:00Z"),
                Instant.parse("2025-06-16T18:00:00Z")));
        leaveRepository.save(mkLeave(USER, "L010B", "REVOKE", false,
                Instant.parse("2025-06-14T08:00:00Z"),
                Instant.parse("2025-06-16T18:00:00Z")));

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).isEmpty();
    }

    // ============================================================
    // 11) userid 隔离
    // ============================================================
    @Test
    @DisplayName("userid 隔离: 只返回指定 userid 的记录")
    void covering_userIsolation() {
        Instant[] win = utcDayWindow("2025-06-15");
        leaveRepository.save(mkLeave(USER, "L011-MINE", "NORMAL", false,
                Instant.parse("2025-06-14T08:00:00Z"),
                Instant.parse("2025-06-16T18:00:00Z")));
        leaveRepository.save(mkLeave(OTHER_USER, "L011-OTHER", "NORMAL", false,
                Instant.parse("2025-06-14T08:00:00Z"),
                Instant.parse("2025-06-16T18:00:00Z")));

        List<DingTalkLeave> mine = leaveRepository.findCovering(USER, win[0], win[1]);
        List<DingTalkLeave> others = leaveRepository.findCovering(OTHER_USER, win[0], win[1]);

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getLeaveId()).isEqualTo("L011-MINE");
        assertThat(others).hasSize(1);
        assertThat(others.get(0).getLeaveId()).isEqualTo("L011-OTHER");
    }

    // ============================================================
    // 12) 多条命中 + start_time ASC 排序
    // ============================================================
    @Test
    @DisplayName("多条命中: 按 start_time ASC 排序")
    void covering_multipleRecords_sortedAsc() {
        Instant[] win = utcDayWindow("2025-06-15");
        // 故意倒序插入, 验证 ORDER BY
        leaveRepository.save(mkLeave(USER, "L012-LATE", "NORMAL", false,
                Instant.parse("2025-06-15T18:00:00Z"),
                Instant.parse("2025-06-15T20:00:00Z")));
        leaveRepository.save(mkLeave(USER, "L012-EARLY", "NORMAL", false,
                Instant.parse("2025-06-15T09:00:00Z"),
                Instant.parse("2025-06-15T12:00:00Z")));
        leaveRepository.save(mkLeave(USER, "L012-NOEND", "NORMAL", false,
                Instant.parse("2025-06-15T14:00:00Z"),
                null));

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).hasSize(3);
        assertThat(r).extracting(DingTalkLeave::getLeaveId)
                .containsExactly("L012-EARLY", "L012-NOEND", "L012-LATE");
    }

    // ============================================================
    // 13) 0 命中 (无记录)
    // ============================================================
    @Test
    @DisplayName("无任何记录: 返回空")
    void covering_noRecords_empty() {
        Instant[] win = utcDayWindow("2025-06-15");
        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).isEmpty();
    }

    // ============================================================
    // 14) start < dayStart 但 end > dayStart (前日 22:00 → 当日 02:00 跨夜)
    // ============================================================
    @Test
    @DisplayName("跨夜请假 (前日 22:00 → 当日 02:00): 命中")
    void covering_overnight_hit() {
        Instant[] win = utcDayWindow("2025-06-15");
        leaveRepository.save(mkLeave(USER, "L014", "NORMAL", false,
                Instant.parse("2025-06-14T22:00:00Z"),
                Instant.parse("2025-06-15T02:00:00Z")));

        List<DingTalkLeave> r = leaveRepository.findCovering(USER, win[0], win[1]);
        assertThat(r).hasSize(1);
    }
}
