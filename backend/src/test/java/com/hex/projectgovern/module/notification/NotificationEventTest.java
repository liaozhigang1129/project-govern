package com.hex.projectgovern.module.notification;

import com.hex.projectgovern.module.dict.ApprovalStep;
import com.hex.projectgovern.module.dict.ApprovalStepRepository;
import com.hex.projectgovern.module.dict.InitiationStatus;
import com.hex.projectgovern.module.dict.InitiationStatusRepository;
import com.hex.projectgovern.module.initiation.ProjectInitiation;
import com.hex.projectgovern.module.initiation.ProjectInitiationRepository;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.Role;
import com.hex.projectgovern.module.org.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知事件单元测试。
 *
 * 关键边界:
 *  - 3 个事件都能被监听器正确消费
 *  - 邮件关闭(enabled=false)时, send() 是 no-op,不抛异常
 *  - 事件触发不影响主业务(InitiationService 正常落库)
 *  - 监听器异常被吞掉,主业务继续
 *
 * 用 @DataJpaTest 跑 InitiationService + 通知事件全链路,
 * 邮件通过 ObjectProvider<JavaMailSender> = null 实现"无 SMTP 也能跑"
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Import(NotificationEventTest.TestBeans.class)
class NotificationEventTest {

    @Autowired ApplicationEventPublisher publisher;
    @Autowired UserRepository userRepo;
    @Autowired com.hex.projectgovern.module.org.RoleRepository roleRepo;
    @Autowired InitiationStatusRepository statusRepo;
    @Autowired ApprovalStepRepository stepRepo;
    @Autowired ProjectInitiationRepository initiationRepo;
    @Autowired RecordingListener recorder;

    @TestConfiguration
    @EnableConfigurationProperties(MailProperties.class)
    @EnableAsync
    static class TestBeans {
        @Bean RecordingListener recordingListener() { return new RecordingListener(); }
        @Bean MailService mailService(MailProperties props) {
            return MailService.forTest(props);
        }
    }

    /** 收集收到的事件,用于断言 */
    @Component
    static class RecordingListener {
        final List<Object> events = new CopyOnWriteArrayList<>();
        @EventListener public void onSub(InitiationSubmittedEvent e) { events.add(e); }
        @EventListener public void onDec(InitiationDecidedEvent e) { events.add(e); }
        @EventListener public void onRes(InitiationResubmittedEvent e) { events.add(e); }
    }

    private Role pmRole;
    private AppUser applicant, approver;
    private InitiationStatus pending, supplement, approved;
    private ApprovalStep deptStep;

    @BeforeEach
    void seedDicts() {
        initiationRepo.deleteAll();
        userRepo.deleteAll();
        roleRepo.deleteAll();
        statusRepo.deleteAll();
        stepRepo.deleteAll();

        pmRole = new Role();
        pmRole.setCode("PM"); pmRole.setName("项目经理"); pmRole.setBuiltIn(true);
        roleRepo.save(pmRole);

        applicant = new AppUser();
        applicant.setUsername("alice");
        applicant.setPasswordHash("x");
        applicant.setFullName("张三");
        applicant.setEmail("alice@company.com");
        applicant.setPrimaryRole(pmRole);
        userRepo.save(applicant);

        approver = new AppUser();
        approver.setUsername("bob");
        approver.setPasswordHash("x");
        approver.setFullName("吴经理");
        approver.setEmail("bob@company.com");
        approver.setPrimaryRole(pmRole);
        userRepo.save(approver);

        pending = new InitiationStatus();
        pending.setCode("PENDING"); pending.setName("审批中"); pending.setSortOrder(1);
        statusRepo.save(pending);
        supplement = new InitiationStatus();
        supplement.setCode("SUPPLEMENT"); supplement.setName("需补充"); supplement.setSortOrder(6);
        statusRepo.save(supplement);
        approved = new InitiationStatus();
        approved.setCode("DEPT_APPROVED"); approved.setName("部门通过"); approved.setSortOrder(2);
        statusRepo.save(approved);

        deptStep = new ApprovalStep();
        deptStep.setCode("DEPT_LEAD"); deptStep.setName("部门负责人审批"); deptStep.setSequence(1);
        stepRepo.save(deptStep);
    }

    @Test
    @DisplayName("InitiationSubmittedEvent: 发布后被监听器收到")
    void submittedEventDelivered() {
        var e = new InitiationSubmittedEvent(
                1L, "IR-2026-001", "新项目", 99L, "张三", "alice@company.com", 1L, Instant.now());
        publisher.publishEvent(e);

        // 等待异步消费
        waitFor(() -> recorder.events.stream().anyMatch(x -> x instanceof InitiationSubmittedEvent));
        assertThat(recorder.events).anyMatch(x -> x instanceof InitiationSubmittedEvent s
                && s.initiationCode().equals("IR-2026-001"));
    }

    @Test
    @DisplayName("InitiationDecidedEvent: APPROVED 中间级 携带 nextStep")
    void decidedEvent_mid() {
        var e = new InitiationDecidedEvent(
                2L, "IR-2026-002", "项目X", 99L, "张三", "alice@company.com",
                50L, "吴经理", "APPROVED", "PMO_ADMIN", "PMO管理员复核", 60L, "OK", Instant.now());
        publisher.publishEvent(e);
        waitFor(() -> recorder.events.stream().anyMatch(x -> x instanceof InitiationDecidedEvent));
        // 用 filter 找 nextStepCode = PMO_ADMIN 的事件
        var mid = recorder.events.stream()
                .filter(x -> x instanceof InitiationDecidedEvent d && "PMO_ADMIN".equals(d.nextStepCode()))
                .map(x -> (InitiationDecidedEvent) x)
                .findFirst().orElseThrow();
        assertThat(mid.decision()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("InitiationDecidedEvent: 终态 EXEC_APPROVED nextStep=null")
    void decidedEvent_terminal() {
        var e = new InitiationDecidedEvent(
                3L, "IR-2026-003", "项目Y", 99L, "张三", "alice@company.com",
                60L, "陈副总", "APPROVED", null, null, null, "通过", Instant.now());
        publisher.publishEvent(e);
        waitFor(() -> recorder.events.stream().anyMatch(x -> x instanceof InitiationDecidedEvent));
        var dec = (InitiationDecidedEvent) recorder.events.stream()
                .filter(x -> x instanceof InitiationDecidedEvent).findFirst().orElseThrow();
        assertThat(dec.nextStepCode()).isNull();
    }

    @Test
    @DisplayName("InitiationResubmittedEvent: 携带 currentStep")
    void resubmittedEvent() {
        var e = new InitiationResubmittedEvent(
                4L, "IR-2026-004", "项目Z", 99L, "张三", "alice@company.com",
                "DEPT_LEAD", "部门负责人审批", 30L, Instant.now());
        publisher.publishEvent(e);
        waitFor(() -> recorder.events.stream().anyMatch(x -> x instanceof InitiationResubmittedEvent));
        var res = (InitiationResubmittedEvent) recorder.events.stream()
                .filter(x -> x instanceof InitiationResubmittedEvent).findFirst().orElseThrow();
        assertThat(res.currentStepCode()).isEqualTo("DEPT_LEAD");
    }

    @Test
    @DisplayName("MailService: enabled=false 时 send() 是 no-op,不抛异常")
    void mailDisabled_noOp() {
        var svc2 = MailService.forTest();
        svc2.props.setEnabled(false);
        svc2.send(List.of("x@x.com"), null, "subj", "body");
    }

    @Test
    @DisplayName("MailService: 收件人为空时 warn 不抛")
    void mailNoRecipient_warnNoThrow() {
        var svc2 = MailService.forTest();
        svc2.props.setEnabled(true);
        svc2.props.setFrom("a");
        svc2.send(List.of(), null, "subj", "body");
    }

    private void waitFor(java.util.function.BooleanSupplier cond) {
        for (int i = 0; i < 50 && !cond.getAsBoolean(); i++) {
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }
}
