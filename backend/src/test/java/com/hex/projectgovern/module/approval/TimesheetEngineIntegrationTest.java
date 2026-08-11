package com.hex.projectgovern.module.approval;

import com.hex.projectgovern.module.timesheet.TimesheetStatus;
import com.hex.projectgovern.module.timesheet.TimesheetWeek;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 TimesheetApprovalAdapter 与 ApprovalEngine 的集成:
 * startTimesheet / approveTimesheet / rejectTimesheet / findInstance
 * 全部委托引擎
 */
class TimesheetEngineIntegrationTest {

    private ApprovalEngine engine;
    private TimesheetApprovalAdapter adapter;

    @BeforeEach
    void setUp() {
        engine = mock(ApprovalEngine.class);
        adapter = new TimesheetApprovalAdapter(engine);
    }

    @Test
    @DisplayName("startTimesheet: 调用 engine.start(kind=\"timesheet\", flowCode=\"STANDARD_TIMESHEET\")")
    void startDelegates() {
        TimesheetWeek t = new TimesheetWeek();
        t.setId(100L);
        t.setUserId(50L);
        t.setWeekStart(LocalDate.of(2026, 8, 4));
        t.setWeekEnd(LocalDate.of(2026, 8, 10));

        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setId(200L);
        when(engine.start(anyString(), anyString(), anyLong(), anyString(), anyLong(), any(), any()))
            .thenReturn(stub);

        Long instanceId = adapter.startTimesheet(t);
        assertThat(instanceId).isEqualTo(200L);
    }

    @Test
    @DisplayName("startTimesheet: 资源码格式 T-{userId}-{weekStart}")
    void startResourceCodeFormat() {
        TimesheetWeek t = new TimesheetWeek();
        t.setId(1L);
        t.setUserId(99L);
        t.setWeekStart(LocalDate.of(2026, 8, 4));
        t.setWeekEnd(LocalDate.of(2026, 8, 10));

        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setId(1L);
        when(engine.start(anyString(), anyString(), anyLong(), anyString(), anyLong(), any(), any()))
            .thenReturn(stub);

        adapter.startTimesheet(t);

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        verify(engine).start(anyString(), anyString(), anyLong(), codeCap.capture(),
            anyLong(), any(), any());
        assertThat(codeCap.getValue()).isEqualTo("T-99-2026-08-04");
    }

    @Test
    @DisplayName("approveTimesheet: 委托 engine.decide(APPROVED) + 终态回传")
    void approveDelegates() {
        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setStatus(ApprovalStatus.APPROVED);
        when(engine.decide(anyLong(), anyLong(), any(), any())).thenReturn(stub);

        ApprovalFlowInstance result = adapter.approveTimesheet(5L, 100L, "ok");
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);

        verify(engine).decide(eq(5L), eq(100L), eq(ApprovalDecision.APPROVED), eq("ok"));
    }

    @Test
    @DisplayName("rejectTimesheet: 委托 engine.decide(REJECTED) + reason 作 comment")
    void rejectDelegates() {
        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setStatus(ApprovalStatus.REJECTED);
        when(engine.decide(anyLong(), anyLong(), any(), any())).thenReturn(stub);

        ApprovalFlowInstance result = adapter.rejectTimesheet(5L, 100L, "材料不全");
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.REJECTED);

        verify(engine).decide(eq(5L), eq(100L), eq(ApprovalDecision.REJECTED), eq("材料不全"));
    }

    @Test
    @DisplayName("findInstance: 委托 engine.findByBiz(kind=\"timesheet\")")
    void findInstanceDelegates() {
        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setId(99L);
        when(engine.findByBiz("timesheet", 50L)).thenReturn(stub);

        assertThat(adapter.findInstance(50L)).isSameAs(stub);
        assertThat(adapter.findInstance(999L)).isNull();  // 未实例化
    }
}