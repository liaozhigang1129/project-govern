package com.hex.projectgovern.module.approval;

import com.hex.projectgovern.module.initiation.ProjectInitiation;
import com.hex.projectgovern.module.approval.event.ApprovalStepActivatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * InitiationApprovalAdapter 单元测试
 * 验证: start → 委托 ApprovalEngine.start(kind="init", flowCode="STANDARD_INITIATION", ...)
 */
class InitiationApprovalAdapterTest {

    private ApprovalEngine engine;
    private InitiationApprovalAdapter adapter;
    private RecordingEventPublisher events;

    @BeforeEach
    void setUp() {
        engine = mock(ApprovalEngine.class);
        events = new RecordingEventPublisher();
        adapter = new InitiationApprovalAdapter(engine);
    }

    @Test
    @DisplayName("startInitiation: 委托 ApprovalEngine.start, 返回 instanceId")
    void startDelegatesToEngine() {
        ProjectInitiation i = new ProjectInitiation();
        i.setId(42L);
        i.setCode("PRJ-042");
        i.setApplicantId(50L);
        i.setDepartmentId(10L);
        i.setContractAmount(new BigDecimal("50000"));

        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setId(99L);
        stub.setCurrentStepNo(1);
        when(engine.start(anyString(), anyString(), anyLong(), anyString(), anyLong(), any(), any()))
            .thenReturn(stub);

        Long instanceId = adapter.startInitiation(i);
        assertThat(instanceId).isEqualTo(99L);
    }

    @Test
    @DisplayName("startInitiation: payload 含 amount (供 skip_when 解析)")
    void startPayloadIncludesAmount() {
        ProjectInitiation i = new ProjectInitiation();
        i.setId(1L);
        i.setCode("X");
        i.setApplicantId(1L);
        i.setDepartmentId(1L);
        i.setContractAmount(new BigDecimal("123.45"));

        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setId(2L);
        when(engine.start(anyString(), anyString(), anyLong(), anyString(), anyLong(), any(), anyString()))
            .thenReturn(stub);

        adapter.startInitiation(i);
        // 验证传给 engine 的 payload 含 amount=123.45
        org.mockito.ArgumentCaptor<String> payloadCap = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(engine).start(
            org.mockito.ArgumentMatchers.eq("init"),
            org.mockito.ArgumentMatchers.eq("STANDARD_INITIATION"),
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq("X"),
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq(1L),
            payloadCap.capture());
        assertThat(payloadCap.getValue()).contains("\"amount\":123.45");
    }

    @Test
    @DisplayName("findInstance: 委托 ApprovalEngine.findByBiz")
    void findInstanceDelegates() {
        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setId(5L);
        when(engine.findByBiz("init", 100L)).thenReturn(stub);

        ApprovalFlowInstance result = adapter.findInstance(100L);
        assertThat(result).isSameAs(stub);
        assertThat(result.getId()).isEqualTo(5L);

        // 不存在的 business
        when(engine.findByBiz("init", 99999L)).thenReturn(null);
        assertThat(adapter.findInstance(99999L)).isNull();
    }

    @Test
    @DisplayName("startInitiation: amount=null 时 payload 用 0 兜底")
    void startPayloadDefaultsToZero() {
        ProjectInitiation i = new ProjectInitiation();
        i.setId(1L);
        i.setCode("X");
        i.setApplicantId(1L);
        i.setDepartmentId(1L);
        i.setContractAmount(null);

        ApprovalFlowInstance stub = new ApprovalFlowInstance();
        stub.setId(2L);
        when(engine.start(anyString(), anyString(), anyLong(), anyString(), anyLong(), any(), anyString()))
            .thenReturn(stub);

        adapter.startInitiation(i);

        org.mockito.ArgumentCaptor<String> payloadCap = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(engine).start(
            anyString(), anyString(), anyLong(), anyString(), anyLong(), any(), payloadCap.capture());
        assertThat(payloadCap.getValue()).contains("\"amount\":0");
    }

    /** 录制 publisher */
    static class RecordingEventPublisher implements ApplicationEventPublisher {
        List<Object> recorded = new ArrayList<>();
        public void publishEvent(Object event) { recorded.add(event); }
    }
}