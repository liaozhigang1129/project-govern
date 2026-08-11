package com.hex.projectgovern.module.initiation;

import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.module.dict.*;
import com.hex.projectgovern.module.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * InitiationService 核心业务:3 级审批状态机。
 *
 * 流程:DEPT_LEAD -> PMO_ADMIN -> EXEC
 *  - APPROVED: 推进到下一步
 *  - REJECTED: 终止于 REJECTED
 *  - SUPPLEMENT: 留在当前步骤,等补材料
 *  - 终态 (EXEC_APPROVED/REJECTED) 再 decide 抛 BusinessException
 *  - 终态 EXEC_APPROVED 时自动创建项目
 */
@DataJpaTest
@AutoConfigureTestDatabase
@Import({InitiationService.class, InitiationAiWbsService.class, InitiationSowFileService.class, com.hex.projectgovern.module.approval.InitiationApprovalAdapter.class, com.hex.projectgovern.module.approval.DefaultApprovalEngine.class, com.hex.projectgovern.module.approval.DefaultApproverResolver.class, com.hex.projectgovern.module.approval.DefaultSkipConditionEvaluator.class, com.hex.projectgovern.module.risk.RiskRuleCache.class, com.hex.projectgovern.module.risk.RiskRuleController.class, com.fasterxml.jackson.databind.ObjectMapper.class})
@ActiveProfiles("test")
class InitiationServiceTest {

    @Autowired InitiationService initiationService;
    @Autowired ProjectInitiationRepository initiationRepo;
    @Autowired ApprovalRecordRepository approvalRepo;
    @Autowired ProjectRepository projectRepository;

    @Autowired InitiationStatusRepository statusRepo;
    @Autowired ApprovalStepRepository stepRepo;
    @Autowired ProjectTypeRepository typeRepo;
    @Autowired ProjectStatusRepository projectStatusRepo;

    private Map<String, Long> s; // status codes -> ids
    private Map<String, Long> st; // step codes -> ids

    @BeforeEach
    void seedDicts() {
        // initiation status
        s = new java.util.HashMap<>();
        for (var pair : new String[][]{
                {"PENDING", "审批中", "false"},
                {"DEPT_APPROVED", "部门通过", "false"},
                {"PMO_APPROVED", "PMO通过", "false"},
                {"EXEC_APPROVED", "已批准", "true"},
                {"REJECTED", "已驳回", "true"},
                {"SUPPLEMENT", "需补充", "false"},
        }) {
            InitiationStatus x = new InitiationStatus();
            x.setCode(pair[0]); x.setName(pair[1]); x.setTerminal(Boolean.parseBoolean(pair[2]));
            x.setSortOrder(0);
            s.put(pair[0], statusRepo.save(x).getId());
        }
        // approval steps
        st = new java.util.HashMap<>();
        for (var pair : new String[][]{
                {"DEPT_LEAD", "部门负责人审批", "1"},
                {"PMO_ADMIN", "PMO管理员复核", "2"},
                {"EXEC", "分管副总审批", "3"},
        }) {
            ApprovalStep x = new ApprovalStep();
            x.setCode(pair[0]); x.setName(pair[1]); x.setSequence(Integer.parseInt(pair[2]));
            st.put(pair[0], stepRepo.save(x).getId());
        }
        // project type + status (for auto-create)
        ProjectType t = new ProjectType();
        t.setCode("DELIVERY"); t.setName("客户交付");
        typeRepo.save(t);

        ProjectStatus ps = new ProjectStatus();
        ps.setCode("ACTIVE"); ps.setName("执行中"); ps.setTerminal(false);
        projectStatusRepo.save(ps);
    }

    private ProjectInitiation mkInit(String code) {
        ProjectInitiation i = new ProjectInitiation();
        i.setCode(code);
        i.setTitle("Test " + code);
        i.setApplicantId(100L);
        i.setDepartmentId(1L);
        i.setBackground("bg");
        i.setGoals("goals");
        i.setScope("scope");
        return i;
    }

    private InitiationService.ApprovalDecision approved() {
        return new InitiationService.ApprovalDecision("APPROVED", "ok");
    }
    private InitiationService.ApprovalDecision rejected() {
        return new InitiationService.ApprovalDecision("REJECTED", "no");
    }

    @Test
    @DisplayName("submit: 状态置 PENDING, currentStep=DEPT_LEAD, submittedAt 有值")
    void submit_initialState() {
        ProjectInitiation i = initiationService.submit(mkInit("IR-001"));
        assertThat(i.getId()).isNotNull();
        assertThat(i.getStatus().getCode()).isEqualTo("PENDING");
        assertThat(i.getCurrentStep()).isEqualTo("DEPT_LEAD");
        assertThat(i.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("submit: 重名 → 抛 BusinessException")
    void submit_duplicateCode() {
        initiationService.submit(mkInit("IR-DUP"));
        assertThatThrownBy(() -> initiationService.submit(mkInit("IR-DUP")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("IR-DUP");
    }

    @Test
    @DisplayName("decide APPROVED: DEPT_LEAD 通过 → 状态 PMO_APPROVED, currentStep=PMO_ADMIN")
    void decide_approved_firstStep() {
        // 业务规则:DEPT_LEAD 审批后,状态码变为 PMO_APPROVED(等待 PMO_ADMIN 复核),
        // currentStep 跳到 PMO_ADMIN。状态码命名反映"下一步等谁",不是"上一步谁过"。
        ProjectInitiation i = initiationService.submit(mkInit("IR-100"));
        ProjectInitiation after = initiationService.decide(i.getId(), 11L, approved());
        assertThat(after.getStatus().getCode()).isEqualTo("PMO_APPROVED");
        assertThat(after.getCurrentStep()).isEqualTo("PMO_ADMIN");
        assertThat(approvalRepo.findByInitiationIdOrderByDecidedAtAsc(i.getId())).hasSize(1);
    }

    @Test
    @DisplayName("decide APPROVED 3 次 → EXEC_APPROVED + 自动建项目")
    void decide_threeApprovalsCreateProject() {
        long startCount = projectRepository.count();
        ProjectInitiation i = initiationService.submit(mkInit("IR-200"));
        initiationService.decide(i.getId(), 11L, approved()); // DEPT_LEAD
        initiationService.decide(i.getId(), 12L, approved()); // PMO_ADMIN
        ProjectInitiation final_ = initiationService.decide(i.getId(), 13L, approved()); // EXEC

        assertThat(final_.getStatus().getCode()).isEqualTo("EXEC_APPROVED");
        assertThat(final_.getCurrentStep()).isNull();
        assertThat(final_.getClosedAt()).isNotNull();
        assertThat(final_.getProjectId()).isNotNull();
        // 自动建项目
        assertThat(projectRepository.count()).isEqualTo(startCount + 1);
        // 3 条审批记录
        assertThat(approvalRepo.findByInitiationIdOrderByDecidedAtAsc(i.getId())).hasSize(3);
    }

    @Test
    @DisplayName("decide REJECTED: 终态 REJECTED, currentStep 清空")
    void decide_rejectedTerminates() {
        ProjectInitiation i = initiationService.submit(mkInit("IR-300"));
        ProjectInitiation after = initiationService.decide(i.getId(), 11L, rejected());
        assertThat(after.getStatus().getCode()).isEqualTo("REJECTED");
        assertThat(after.getCurrentStep()).isNull();
        assertThat(after.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("decide 终态后再 decide → 抛 BusinessException (幂等终止)")
    void decide_terminalRejectsFurtherDecisions() {
        ProjectInitiation i = initiationService.submit(mkInit("IR-400"));
        initiationService.decide(i.getId(), 11L, rejected()); // 进 REJECTED
        assertThatThrownBy(() -> initiationService.decide(i.getId(), 99L, approved()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    @DisplayName("list: 返回所有未软删的,带 status (JOIN FETCH 防 LAZY)")
    void list_returnsActiveWithStatus() {
        initiationService.submit(mkInit("IR-LIST-1"));
        initiationService.submit(mkInit("IR-LIST-2"));
        var list = initiationService.list();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getStatus().getCode()).isIn("PENDING");
    }

    @Test
    @DisplayName("decide 非法 decision → 抛 BusinessException")
    void decide_invalidDecisionThrows() {
        ProjectInitiation i = initiationService.submit(mkInit("IR-500"));
        assertThatThrownBy(() -> initiationService.decide(i.getId(), 11L,
                new InitiationService.ApprovalDecision("MAYBE", "?")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    @DisplayName("SUPPLEMENT → resubmit → PENDING, currentStep 保持不变")
    void resubmit_supplementGoesBackToPending() {
        ProjectInitiation i = initiationService.submit(mkInit("IR-RES-1"));
        // DEPT_LEAD 打回补材料
        initiationService.decide(i.getId(), 11L,
                new InitiationService.ApprovalDecision("SUPPLEMENT", "请补范围"));
        ProjectInitiation afterSupplement = initiationService.get(i.getId());
        assertThat(afterSupplement.getStatus().getCode()).isEqualTo("SUPPLEMENT");
        assertThat(afterSupplement.getCurrentStep()).isEqualTo("DEPT_LEAD");

        // 申请人补料后重提(注意 applicantId 跟 mkInit 里的 100L 一致)
        ProjectInitiation resubmitted = initiationService.resubmit(i.getId(), 100L);
        assertThat(resubmitted.getStatus().getCode()).isEqualTo("PENDING");
        // currentStep 保持,继续等 DEPT_LEAD 重审
        assertThat(resubmitted.getCurrentStep()).isEqualTo("DEPT_LEAD");
        assertThat(resubmitted.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("resubmit: 非 SUPPLEMENT 状态 → 抛 BusinessException")
    void resubmit_onlyAllowedInSupplement() {
        ProjectInitiation i = initiationService.submit(mkInit("IR-RES-2"));
        // 状态 PENDING,直接调 resubmit 应失败
        assertThatThrownBy(() -> initiationService.resubmit(i.getId(), 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SUPPLEMENT");
    }

    @Test
    @DisplayName("resubmit: 非申请人 → 抛 BusinessException")
    void resubmit_onlyApplicantCanResubmit() {
        ProjectInitiation i = initiationService.submit(mkInit("IR-RES-3"));
        initiationService.decide(i.getId(), 11L,
                new InitiationService.ApprovalDecision("SUPPLEMENT", "请补材料"));
        assertThatThrownBy(() -> initiationService.resubmit(i.getId(), 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("applicant");
    }

    @Test
    @DisplayName("resubmit: 补料后 DEPT_LEAD 再 APPROVED,后续流转照常")
    void resubmit_thenApproveFlowsNormally() {
        ProjectInitiation i = initiationService.submit(mkInit("IR-RES-4"));
        initiationService.decide(i.getId(), 11L,
                new InitiationService.ApprovalDecision("SUPPLEMENT", "补材料"));
        initiationService.resubmit(i.getId(), 100L);
        // 申请人重提后,DEPT_LEAD 这次通过
        ProjectInitiation after = initiationService.decide(i.getId(), 11L, approved());
        assertThat(after.getStatus().getCode()).isEqualTo("PMO_APPROVED");
        assertThat(after.getCurrentStep()).isEqualTo("PMO_ADMIN");
    }
}
