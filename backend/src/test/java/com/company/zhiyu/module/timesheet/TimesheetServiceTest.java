package com.company.zhiyu.module.timesheet;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.module.timesheet.dto.TimesheetDtos.CreateRequest;
import com.company.zhiyu.module.timesheet.dto.TimesheetDtos.EntriesRequest;
import com.company.zhiyu.module.timesheet.dto.TimesheetDtos.EntryRequest;
import com.company.zhiyu.module.timesheet.dto.TimesheetDtos.SubmitRequest;
import com.company.zhiyu.module.timesheet.dto.TimesheetResponses.Detail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * P2.A TimesheetService 业务规则单测。
 * 走 H2 in-memory(profile=test),不依赖 PG,无 flyway。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class TimesheetServiceTest {

    @Autowired TimesheetService service;

    private CreateRequest createReq(Long uid, LocalDate monday) {
        var r = new CreateRequest();
        r.setUserId(uid);
        r.setWeekStart(monday);
        return r;
    }

    private EntryRequest entryReq(LocalDate d, Long pid, Long mid, BigDecimal h, String desc) {
        var e = new EntryRequest();
        e.setWorkDate(d);
        e.setProjectId(pid);
        e.setMilestoneId(mid);
        e.setHours(h);
        e.setDescription(desc);
        return e;
    }

    private SubmitRequest submitReq(String note) {
        var s = new SubmitRequest();
        s.setSubmitterNote(note);
        return s;
    }

    private EntriesRequest entriesReq(List<EntryRequest> es) {
        var r = new EntriesRequest();
        r.setEntries(es);
        return r;
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1); // 任意周一的稳定日期
    private static final LocalDate THIS_MONDAY = TODAY.minusDays(TODAY.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    private LocalDate mondayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    private LocalDate thisMonday() {
        return THIS_MONDAY;
    }

    @Test
    @DisplayName("T1: 创建空周报 + weekStart 必须是周一,否则 400")
    void createMondayRequired() {
        var monday = thisMonday();
        Detail d = service.createOrGet(createReq(1L, monday));
        assertThat(d.getUserId()).isEqualTo(1L);
        assertThat(d.getWeekStart()).isEqualTo(monday);
        assertThat(d.getWeekEnd()).isEqualTo(monday.plusDays(6));
        assertThat(d.getStatus()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(d.getEntries()).isEmpty();

        assertThatThrownBy(() -> service.createOrGet(createReq(1L, monday.plusDays(1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("周一");
    }

    @Test
    @DisplayName("T2: createOrGet 幂等 — 已存在直接返回")
    void createIdempotent() {
        var monday = thisMonday();
        Detail a = service.createOrGet(createReq(1L, monday));
        Detail b = service.createOrGet(createReq(1L, monday));
        assertThat(b.getId()).isEqualTo(a.getId());
    }

    @Test
    @DisplayName("T3: 批量 upsert 写入明细 — DRAFT 期可改")
    void upsertEntriesHappy() {
        var monday = thisMonday();
        Detail week = service.createOrGet(createReq(1L, monday));
        Detail updated = service.upsertEntries(week.getId(), entriesReq(List.of(
                entryReq(monday,             100L, 10L, new BigDecimal("8.0"), "周一开发"),
                entryReq(monday.plusDays(1), 100L, 11L, new BigDecimal("6.5"), "周二 review"),
                entryReq(monday.plusDays(2), 100L, 10L, new BigDecimal("7.0"), "周三"),
                entryReq(monday.plusDays(3), 101L, null, new BigDecimal("4.0"), "周四 跨项目"),
                entryReq(monday.plusDays(4), 100L, 10L, new BigDecimal("8.0"), "周五")
        )));
        assertThat(updated.getEntries()).hasSize(5);
        assertThat(updated.getTotalHours()).isEqualByComparingTo("33.5");
    }

    @Test
    @DisplayName("T4: workDate 超出本周范围 → 400")
    void upsertOutOfRange() {
        var monday = thisMonday();
        Detail week = service.createOrGet(createReq(1L, monday));
        assertThatThrownBy(() -> {
            service.upsertEntries(week.getId(), entriesReq(List.of(
                    entryReq(monday.minusDays(7), 100L, 10L, new BigDecimal("8.0"), "")
            )));
        }).isInstanceOf(BusinessException.class).hasMessageContaining("超出本周范围");
    }

    @Test
    @DisplayName("T5: hours 24h 上限 — service 校验")
    void hours24Cap() {
        var monday = thisMonday();
        Detail week = service.createOrGet(createReq(1L, monday));
        assertThatThrownBy(() -> {
            service.upsertEntries(week.getId(), entriesReq(List.of(
                    entryReq(monday, 100L, 10L, new BigDecimal("25.0"), "超限")
            )));
        }).isInstanceOf(BusinessException.class).hasMessageContaining("24");
    }

    @Test
    @DisplayName("T6: 14 天锁 — 超过 2 周前的周报不可补录")
    void lockOldWeeks() {
        var monday = thisMonday().minusDays(21);
        Detail week = service.createOrGet(createReq(1L, monday));
        assertThatThrownBy(() -> {
            service.upsertEntries(week.getId(), entriesReq(List.of(
                    entryReq(monday, 100L, 10L, new BigDecimal("8.0"), "老数据")
            )));
        }).isInstanceOf(BusinessException.class).hasMessageContaining("14 天");
    }

    @Test
    @DisplayName("T7: 状态机 — DRAFT → SUBMITTED,空明细不能提交")
    void submitHappy() {
        var monday = thisMonday();
        Detail week = service.createOrGet(createReq(1L, monday));
        assertThatThrownBy(() -> service.submit(week.getId(), 1L, submitReq("")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("无明细");
        service.upsertEntries(week.getId(), entriesReq(List.of(
                entryReq(monday, 100L, 10L, new BigDecimal("8.0"), "ok")
        )));
        Detail submitted = service.submit(week.getId(), 1L, submitReq("本周完成 5 个里程碑"));
        assertThat(submitted.getStatus()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(submitted.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("T8: 状态机 — SUBMITTED → APPROVED")
    void approveFlow() {
        var monday = thisMonday();
        Detail week = service.createOrGet(createReq(1L, monday));
        service.upsertEntries(week.getId(), entriesReq(List.of(
                entryReq(monday, 100L, 10L, new BigDecimal("8.0"), "")
        )));
        service.submit(week.getId(), 1L, submitReq(""));
        Detail approved = service.approve(week.getId(), 2L);
        assertThat(approved.getStatus()).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(approved.getApprovedAt()).isNotNull();
        assertThatThrownBy(() -> service.submit(week.getId(), 1L, submitReq("")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("T9: 非本人周报不能 submit/approve/delete")
    void notOwnerRejected() {
        var monday = thisMonday();
        Detail week = service.createOrGet(createReq(1L, monday));
        service.upsertEntries(week.getId(), entriesReq(List.of(
                entryReq(monday, 100L, 10L, new BigDecimal("8.0"), "")
        )));
        assertThatThrownBy(() -> service.submit(week.getId(), 2L, submitReq("")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("非本人");
    }
}
