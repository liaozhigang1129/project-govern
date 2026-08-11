package com.hex.projectgovern.module.alert.engine.impl;

import com.hex.projectgovern.module.alert.AlertEvent;
import com.hex.projectgovern.module.alert.engine.AbstractSqlAlertRule;
import com.hex.projectgovern.module.project.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规则 6/6: 合规违规 (compliance_violation)
 *
 * <p>判定:审批超时 24h / 字典启用但无值 → 触发 LOW 告警
 *
 * <p>v1.0 占位:留给 workflow/dict 模块接入后实现。
 *
 * @since V5.1+ / WP-M5-02 / T-03
 */
@Component
@Slf4j
public class ComplianceViolationAlertRule extends AbstractSqlAlertRule {

    public ComplianceViolationAlertRule(ProjectRepository projectRepository) {
        super(projectRepository);
    }

    @Override public String code() { return "PAYMENT_OVERDUE"; } // 复用现 typeCode
    @Override public String name() { return "合规违规 (审批超时/字典缺失)"; }
    @Override public String severity() { return "LOW"; }
    @Override public double defaultThreshold() { return 24; }

    @Override
    protected List<AlertEvent> evaluateProject(Long projectId) {
        // v1.0 占位 — 等审批超时检测 + 字典一致性检查接入
        return List.of();
    }
}
