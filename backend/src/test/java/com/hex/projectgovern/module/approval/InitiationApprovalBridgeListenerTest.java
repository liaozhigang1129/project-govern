package com.hex.projectgovern.module.approval;

import com.hex.projectgovern.module.approval.event.ApprovalStepActivatedEvent;
import com.hex.projectgovern.module.notification.InitiationSubmittedEvent;
import com.hex.projectgovern.module.notification.TimesheetSubmittedEvent;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * InitiationApprovalBridgeListener 单元测试
 * 验证: ApprovalStepActivatedEvent → InitiationSubmittedEvent / TimesheetSubmittedEvent 转发
 */
class InitiationApprovalBridgeListenerTest {

    private UserRepository userRepository;
    private RecordingEventPublisher publisher;
    private InitiationApprovalBridgeListener listener;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        publisher = new RecordingEventPublisher();
        listener = new InitiationApprovalBridgeListener(userRepository, publisher);
    }

    @Test
    @DisplayName("kind=\"init\" → 转发为 InitiationSubmittedEvent")
    void forwardsInitiation() {
        AppUser applicant = new AppUser();
        applicant.setId(50L);
        applicant.setFullName("张三");
        applicant.setEmail("zhang@example.com");
        when(userRepository.findById(50L)).thenReturn(Optional.of(applicant));

        ApprovalStepActivatedEvent ev = new ApprovalStepActivatedEvent(
            1L, "init", 100L, "PRJ-001", 50L, 1, "部门负责人", "DEPT_LEAD", 200L, Instant.now());
        listener.onApprovalStepActivated(ev);

        assertThat(publisher.recorded).hasSize(1);
        assertThat(publisher.recorded.get(0)).isInstanceOf(InitiationSubmittedEvent.class);
        InitiationSubmittedEvent published = (InitiationSubmittedEvent) publisher.recorded.get(0);
        assertThat(published.initiationId()).isEqualTo(100L);
        assertThat(published.initiationCode()).isEqualTo("PRJ-001");
        assertThat(published.applicantUserId()).isEqualTo(50L);
        assertThat(published.applicantName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("kind=\"timesheet\" → 转发为 TimesheetSubmittedEvent")
    void forwardsTimesheet() {
        AppUser submitter = new AppUser();
        submitter.setId(80L);
        submitter.setFullName("李四");
        when(userRepository.findById(80L)).thenReturn(Optional.of(submitter));

        ApprovalStepActivatedEvent ev = new ApprovalStepActivatedEvent(
            2L, "timesheet", 99L, "T-80-2026-08-04", 80L, 1, "PMO 审核", "PMO_ADMIN", 200L, Instant.now());
        listener.onApprovalStepActivated(ev);

        assertThat(publisher.recorded).hasSize(1);
        assertThat(publisher.recorded.get(0)).isInstanceOf(TimesheetSubmittedEvent.class);
        TimesheetSubmittedEvent published = (TimesheetSubmittedEvent) publisher.recorded.get(0);
        assertThat(published.timesheetId()).isEqualTo(99L);
        assertThat(published.resourceCode()).isEqualTo("T-80-2026-08-04");
        assertThat(published.submitterUserId()).isEqualTo(80L);
        assertThat(published.submitterName()).isEqualTo("李四");
    }

    @Test
    @DisplayName("kind=\"risk\" (未实现) → 跳过,不 publish")
    void skipsUnknownKind() {
        ApprovalStepActivatedEvent ev = new ApprovalStepActivatedEvent(
            3L, "risk", 1L, "RISK-001", 50L, 1, "升级", "PMO", 200L, Instant.now());
        listener.onApprovalStepActivated(ev);
        assertThat(publisher.recorded).isEmpty();
    }

    @Test
    @DisplayName("申请人 DB 不存在 → 兜底 Unknown / null email, 不抛错")
    void handlesMissingUser() {
        when(userRepository.findById(50L)).thenReturn(Optional.empty());

        ApprovalStepActivatedEvent ev = new ApprovalStepActivatedEvent(
            1L, "init", 100L, "PRJ-001", 50L, 1, "部门", "DEPT_LEAD", 200L, Instant.now());
        listener.onApprovalStepActivated(ev);

        assertThat(publisher.recorded).hasSize(1);
        InitiationSubmittedEvent published = (InitiationSubmittedEvent) publisher.recorded.get(0);
        assertThat(published.applicantUserId()).isNull();
        assertThat(published.applicantName()).isEqualTo("Unknown");
        assertThat(published.applicantEmail()).isNull();
    }

    /** 录制 publisher */
    static class RecordingEventPublisher implements ApplicationEventPublisher {
        List<Object> recorded = new ArrayList<>();
        public void publishEvent(Object event) { recorded.add(event); }
    }
}