package com.hex.projectgovern.module.alert.engine.impl;

import com.hex.projectgovern.module.alert.AlertEvent;
import com.hex.projectgovern.module.alert.engine.AbstractSqlAlertRule;
import com.hex.projectgovern.module.finance.CostItemRepository;
import com.hex.projectgovern.module.project.Project;
import com.hex.projectgovern.module.project.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 规则 1/6: 成本超支 (cost_overrun) - 升级版 (P2 #26)
 *
 * <p>判定:本月累计 cost_item > 项目预算 × 阈值 (默认 90%) → 触发 HIGH 告警
 *
 * <p>v1.0 用 reconciliation.totalDiff 作 proxy; 升级后直接读 CostItemRepository
 * 按月 SUM (与财务成本引擎对齐).
 *
 * <p>阈值配置: {@code pmo.alert.cost-overrun.threshold} (默认 0.90)
 *
 * @since V5.1+ / WP-M5-02 / T-03 (v2 升级: 2026-08-11)
 */
@Component
@Slf4j
public class CostOverrunAlertRule extends AbstractSqlAlertRule {

    /** 默认 90% 阈值 (v1.0 兼容) */
    public static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.90");

    private final CostItemRepository costItemRepo;

    /** 阈值 (可被 application.yml 覆盖: pmo.alert.cost-overrun.threshold) */
    @Value("${pmo.alert.cost-overrun.threshold:0.90}")
    private double thresholdPct = 0.90;

    public CostOverrunAlertRule(ProjectRepository projectRepository,
                                 CostItemRepository costItemRepo) {
        super(projectRepository);
        this.costItemRepo = costItemRepo;
    }

    @Override public String code() { return "BUDGET_EXCEED"; }
    @Override public String name() { return "项目预算超阈值"; }
    @Override public String severity() { return "HIGH"; }
    @Override public double defaultThreshold() { return 0.90; }

    @Override
    protected List<AlertEvent> evaluateProject(Long projectId) {
        var opt = projectRepository.findById(projectId);
        if (opt.isEmpty() || opt.get().isDeleted()) return List.of();
        Project p = opt.get();
        BigDecimal budget = p.getBudgetEstimate();
        if (budget == null || budget.signum() <= 0) return List.of();

        // 本月累计成本 (按 CostItem.date SUM)
        YearMonth ym = YearMonth.now();
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        BigDecimal monthCost = costItemRepo.sumByProjectAndDateRange(projectId, from, to);
        if (monthCost == null || monthCost.signum() <= 0) return List.of();

        BigDecimal ratio = monthCost
                .multiply(BigDecimal.valueOf(100))
                .divide(budget, 4, RoundingMode.HALF_UP);
        BigDecimal threshold100 = BigDecimal.valueOf(thresholdPct).multiply(BigDecimal.valueOf(100));
        if (ratio.compareTo(threshold100) < 0) {
            return List.of();
        }

        String msg = String.format(
                "项目 #%d 成本超支预警:本月累计 ¥%s,占预算 ¥%s 的 %s%% (阈值 %s%%)",
                projectId, monthCost.toPlainString(), budget.toPlainString(),
                ratio.toPlainString(), threshold100.toPlainString());

        return List.of(buildEvent(projectId, monthCost, budget, msg));
    }
}
