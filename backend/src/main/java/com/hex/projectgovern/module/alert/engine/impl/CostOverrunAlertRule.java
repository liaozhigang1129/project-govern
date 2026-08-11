package com.hex.projectgovern.module.alert.engine.impl;

import com.hex.projectgovern.module.alert.AlertEvent;
import com.hex.projectgovern.module.alert.engine.AbstractSqlAlertRule;
import com.hex.projectgovern.module.finance.CostItemRepository;
import com.hex.projectgovern.module.finance.dto.FinanceDtos.ReconciliationHealth;
import com.hex.projectgovern.module.finance.ReconciliationService;
import com.hex.projectgovern.module.project.Project;
import com.hex.projectgovern.module.project.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则 1/6: 成本超支 (cost_overrun)
 *
 * <p>判定:本月累计 cost_item > 项目预算 × 90% → 触发 HIGH 告警
 *
 * <p>实现:用项目预算 (project.budget_estimate) × 0.9 对比本月累计成本
 * (cost_item 当前月 SUM amount)。简化:这里用 reconciliation health.totalDiff
 * 作为代理指标(v1.0 阶段),后续接 cost_calculation 后替换。
 *
 * @since V5.1+ / WP-M5-02 / T-03
 */
@Component
@Slf4j
public class CostOverrunAlertRule extends AbstractSqlAlertRule {

    /** 90% 阈值 */
    public static final BigDecimal THRESHOLD_PCT = new BigDecimal("0.90");

    private final ReconciliationService reconciliationService;

    public CostOverrunAlertRule(ProjectRepository projectRepository,
                                 ReconciliationService reconciliationService) {
        super(projectRepository);
        this.reconciliationService = reconciliationService;
    }

    @Override public String code() { return "BUDGET_EXCEED"; }
    @Override public String name() { return "项目预算超 90%"; }
    @Override public String severity() { return "HIGH"; }
    @Override public double defaultThreshold() { return 0.90; }

    @Override
    protected List<AlertEvent> evaluateProject(Long projectId) {
        var opt = projectRepository.findById(projectId);
        if (opt.isEmpty() || opt.get().isDeleted()) return List.of();
        Project p = opt.get();
        BigDecimal budget = p.getBudgetEstimate();
        if (budget == null || budget.signum() <= 0) return List.of();

        // 取本月对账健康度聚合的总差异 (v1.0 用差异作 proxy;真实成本需 cost_calculation)
        ReconciliationHealth h = reconciliationService.health(projectId);
        // 简化为:本月若 mismatch 数 > 0 即视为成本可能超支
        // 这里仍按 budget 占比评估,但 mvp 用 budget vs reconciliation.totalDiff 差额
        if (h.totalDiff().signum() == 0) return List.of();

        BigDecimal ratio = h.totalDiff()
                .multiply(BigDecimal.valueOf(100))
                .divide(budget, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(THRESHOLD_PCT.multiply(BigDecimal.valueOf(100))) < 0) {
            return List.of();
        }

        String msg = String.format(
                "项目 #%d 成本超支预警:本月累计差异 ¥%s,占预算 ¥%s 的 %s%% (阈值 %s%%)",
                projectId, h.totalDiff().toPlainString(), budget.toPlainString(),
                ratio.toPlainString(), THRESHOLD_PCT.multiply(BigDecimal.valueOf(100)).toPlainString());

        return List.of(buildEvent(projectId, h.totalDiff(), budget, msg));
    }
}
