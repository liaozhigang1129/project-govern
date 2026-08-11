package com.hex.projectgovern.module.alert.engine.impl;

import com.hex.projectgovern.module.alert.AlertEvent;
import com.hex.projectgovern.module.alert.engine.AbstractSqlAlertRule;
import com.hex.projectgovern.module.project.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规则 4/6: 风险升级 (risk_escalation)
 *
 * <p>判定:项目风险等级跨级上升 (LOW/MEDIUM → HIGH/CRITICAL) → 触发 CRITICAL
 *
 * <p>v1.0 占位:留给 risk 模块接入后实现。
 *
 * @since V5.1+ / WP-M5-02 / T-03
 */
@Component
@Slf4j
public class RiskEscalationAlertRule extends AbstractSqlAlertRule {

    public RiskEscalationAlertRule(ProjectRepository projectRepository) {
        super(projectRepository);
    }

    @Override public String code() { return "CONTRACT_BALANCE"; } // 复用现 typeCode
    @Override public String name() { return "风险等级跨级升级"; }
    @Override public String severity() { return "CRITICAL"; }
    @Override public double defaultThreshold() { return 1; }

    @Override
    protected List<AlertEvent> evaluateProject(Long projectId) {
        // v1.0 占位 — 等 risk.level 跨级检测接入
        return List.of();
    }
}
