package com.hex.projectgovern.module.approval;

import com.hex.projectgovern.module.timesheet.TimesheetWeek;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TimesheetApprovalAdapter 单元测试
 */
class TimesheetApprovalAdapterTest {

    private ApprovalEngine engine;
    private TimesheetApprovalAdapter adapter;

    @BeforeEach
    void setUp() {
        engine = mock(ApprovalEngine.class);
        adapter = new TimesheetApprovalAdapter(engine);
    }

    @Test
    @DisplayName("startTimesheet: 委托 engine.start(kind=\"timesheet\", flowCode=\"STANDARD_TIMESHEET\")")
    void startDelegates() {
        TimesheetWeek t = new TimesheetWeek();
        t.setId(99L);
        t.setUserId(50L);
        t.setWeekStart(LocalDate.of(2026, 8, 4));
        t.setWeekEnd(LocalDate.of(2026, 8, 10));

        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setId(77L);
        when(engine.start(anyString(), anyString(), anyLong(), anyString(), anyLong(), any(), any()))
            .thenReturn(stub);

        Long instanceId = adapter.startTimesheet(t);
        assertThat(instanceId).isEqualTo(77L);

    org.mockito.ArgumentCaptor<String> flowCodeCap = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(engine).start(
            org.mockito.ArgumentMatchers.eq("timesheet"),
            flowCodeCap.capture(),
            org.mockito.ArgumentMatchers.eq(99L),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(50L),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.anyString());
        assertThat(flowCodeCap.getValue()).isEqualTo("STANDARD_TIMESHEET");
    }

    @Test
    @DisplayName("startTimesheet: payload 含 timesheetId + weekStart + weekEnd")
    void startPayloadIncludesFields() {
        TimesheetWeek t = new TimesheetWeek();
        t.setId(1L);
        t.setUserId(1L);
        t.setWeekStart(LocalDate.of(2026, 8, 4));
        t.setWeekEnd(LocalDate.of(2026, 8, 10));

        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        when(engine.start(anyString(), anyString(), anyLong(), anyString(), anyLong(), any(), anyString()))
            .thenReturn(stub);

        adapter.startTimesheet(t);

        org.mockito.ArgumentCaptor<String> payloadCap = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(engine).start(
            anyString(), anyString(), anyLong(), anyString(), anyLong(), any(), payloadCap.capture());
        String payload = payloadCap.getValue();
        assertThat(payload).contains("\"timesheetId\":1");
        assertThat(payload).contains("\"weekStart\":\"2026-08-04\"");
        assertThat(payload).contains("\"weekEnd\":\"2026-08-10\"");
    }

    @Test
    @DisplayName("approveTimesheet: 委托 engine.decide(APPROVED) → 返回终态 instance")
    void approveDelegates() {
        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setStatus(ApprovalStatus.APPROVED);
        when(engine.decide(anyLong(), anyLong(), any(), any())).thenReturn(stub);

        ApprovalFlowInstance result = adapter.approveTimesheet(5L, 100L, "ok");
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);

        org.mockito.Mockito.verify(engine).decide(
            org.mockito.ArgumentMatchers.eq(5L),
            org.mockito.ArgumentMatchers.eq(100L),
            org.mockito.ArgumentMatchers.eq(ApprovalDecision.APPROVED),
            org.mockito.ArgumentMatchers.eq("ok"));
    }

    @Test
    @DisplayName("rejectTimesheet: 委托 engine.decide(REJECTED) + reason 作 comment")
    void rejectDelegates() {
        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setStatus(ApprovalStatus.REJECTED);
        when(engine.decide(anyLong(), anyLong(), any(), any())).thenReturn(stub);

        ApprovalFlowInstance result = adapter.rejectTimesheet(5L, 100L, "理由不足");
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.REJECTED);

        org.mockito.Mockito.verify(engine).decide(
            org.mockito.ArgumentMatchers.eq(5L),
            org.mockito.ArgumentMatchers.eq(100L),
            org.mockito.ArgumentMatchers.eq(ApprovalDecision.REJECTED),
            org.mockito.ArgumentMatchers.eq("理由不足"));
    }

    @Test
    @DisplayName("findInstance: 委托 engine.findByBiz(kind=\"timesheet\")")
    void findInstanceDelegates() {
        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setId(8L);
        when(engine.findByBiz("timesheet", 99L)).thenReturn(stub);

        ApprovalFlowInstance result = adapter.findInstance(99L);
        assertThat(result).isSameAs(stub);
    }
}