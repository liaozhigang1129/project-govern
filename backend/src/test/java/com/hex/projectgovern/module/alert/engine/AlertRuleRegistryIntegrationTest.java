package com.hex.projectgovern.module.alert.engine;

import com.hex.projectgovern.module.alert.engine.impl.*;
import com.hex.projectgovern.module.alert.AlertEvent;
import com.hex.projectgovern.module.finance.CostItemRepository;
import com.hex.projectgovern.module.finance.ReconciliationService;
import com.hex.projectgovern.module.project.Project;
import com.hex.projectgovern.module.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import static org.mockito.ArgumentMatchers.eq;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 6 类规则 + 注册中心 集成测试 (WP-M5-02 / T-03)
 *
 * 验证 6 个规则 bean:
 *  - 全部成功注册到 AlertRuleRegistry
 *  - code() / severity() / name() 字段合规
 *  - 至少 1 个规则(budget_overrun)在 project.budget=0 / reconciliation.totalDiff=0 时返回空
 */
class AlertRuleRegistryIntegrationTest {

    private ProjectRepository projectRepo;
    private ReconciliationService reconciliationService;
    private CostItemRepository costItemRepo;
    private AlertRuleRegistry registry;

    @BeforeEach
    void setUp() {
        projectRepo = mock(ProjectRepository.class);
        costItemRepo = mock(CostItemRepository.class);
        when(projectRepo.findAll()).thenReturn(List.of());

        registry = new AlertRuleRegistry(List.of(
                new CostOverrunAlertRule(projectRepo, costItemRepo),
                new ScheduleDelayAlertRule(projectRepo),
                new QualityIssueAlertRule(projectRepo),
                new RiskEscalationAlertRule(projectRepo),
                new ResourceOverloadAlertRule(projectRepo),
                new ComplianceViolationAlertRule(projectRepo)));
        registry.init();
    }

    @Test
    @DisplayName("6 规则全部注册到 registry")
    void allSixRegistered() {
        assertThat(registry.size()).isEqualTo(6);
        assertThat(registry.all()).hasSize(6);
    }

    @Test
    @DisplayName("6 个规则 code 各不相同")
    void distinctCodes() {
        var codes = registry.all().stream().map(AlertRule::code).toList();
        assertThat(codes).doesNotHaveDuplicates();
        assertThat(codes).contains(
                "BUDGET_EXCEED", "PROJECT_STALE", "ROLE_DEFAULT",
                "CONTRACT_BALANCE", "HOURS_OVER", "PAYMENT_OVERDUE");
    }

    @Test
    @DisplayName("每个规则的 severity 在合法集合内")
    void validSeverity() {
        for (AlertRule r : registry.all()) {
            assertThat(r.severity())
                    .as("rule %s severity", r.code())
                    .isIn("HIGH", "MEDIUM", "LOW", "CRITICAL");
        }
    }

    @Test
    @DisplayName("CostOverrunAlertRule: 空 project + 零差异 → 不触发")
    void costOverrun_noTrigger_whenNoData() {
        // 实际场景:projectRepo.findAll() 返回空 (setUp 已 mock)
        var events = registry.get("BUDGET_EXCEED").get().evaluate();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("ScheduleDelay/Quality/Risk/Resource/Compliance 占位规则: 始终空")
    void placeholderRules_returnEmpty() {
        for (String code : List.of("PROJECT_STALE", "ROLE_DEFAULT",
                "CONTRACT_BALANCE", "HOURS_OVER", "PAYMENT_OVERDUE")) {
            var events = registry.get(code).get().evaluate();
            assertThat(events).as("placeholder rule %s should return empty", code).isEmpty();
        }
    }

    @Test
    @DisplayName("CostOverrunAlertRule: project 有预算 + reconciliation 有差异 → 触发")
    void costOverrun_triggersWhenDiffExists() {
        Project p = mock(Project.class);
        when(p.getId()).thenReturn(100L);
        when(p.isDeleted()).thenReturn(false);
        when(p.getBudgetEstimate()).thenReturn(new BigDecimal("10000.00"));
        when(projectRepo.findAll()).thenReturn(List.of(p));
        when(projectRepo.findById(100L)).thenReturn(java.util.Optional.of(p));

        // reconciliation.totalDiff = ¥9500 (> 90% of ¥10000)
        when(costItemRepo.sumByProjectAndDateRange(eq(100L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("9500.00"));

        var events = registry.get("BUDGET_EXCEED").get().evaluate();

        assertThat(events).hasSize(1);
        AlertEvent e = events.get(0);
        assertThat(e.getProjectId()).isEqualTo(100L);
        assertThat(e.getActualValue()).isEqualByComparingTo("9500.00");
        assertThat(e.getMessage()).contains("100").contains("成本超支");
    }

    @Test
    @DisplayName("CostOverrunAlertRule: 差异小于阈值 → 不触发")
    void costOverrun_noTrigger_belowThreshold() {
        Project p = mock(Project.class);
        when(p.getId()).thenReturn(100L);
        when(p.isDeleted()).thenReturn(false);
        when(p.getBudgetEstimate()).thenReturn(new BigDecimal("10000.00"));
        when(projectRepo.findAll()).thenReturn(List.of(p));
        when(projectRepo.findById(100L)).thenReturn(java.util.Optional.of(p));
        // ¥500 (< 90% of ¥10000)
        when(costItemRepo.sumByProjectAndDateRange(eq(100L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("500.00"));

        var events = registry.get("BUDGET_EXCEED").get().evaluate();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("CostOverrunAlertRule: budget=0 → 不触发 (避免除 0)")
    void costOverrun_noBudget_noTrigger() {
        Project p = mock(Project.class);
        when(p.getId()).thenReturn(100L);
        when(p.isDeleted()).thenReturn(false);
        when(p.getBudgetEstimate()).thenReturn(BigDecimal.ZERO);
        when(projectRepo.findAll()).thenReturn(List.of(p));

        var events = registry.get("BUDGET_EXCEED").get().evaluate();
        assertThat(events).isEmpty();
    }
}
