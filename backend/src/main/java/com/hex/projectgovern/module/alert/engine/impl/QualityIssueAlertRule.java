package com.hex.projectgovern.module.alert.engine.impl;

import com.hex.projectgovern.module.alert.AlertEvent;
import com.hex.projectgovern.module.alert.engine.AbstractSqlAlertRule;
import com.hex.projectgovern.module.project.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规则 3/6: 质量事件 (quality_issue)
 *
 * <p>判定:项目下 MAJOR+ 缺陷数 > 5 → 触发 HIGH 告警
 *
 * <p>v1.0 占位:留给 defect / qa 模块接入后实现。
 *
 * @since V5.1+ / WP-M5-02 / T-03
 */
@Component
@Slf4j
public class QualityIssueAlertRule extends AbstractSqlAlertRule {

    public QualityIssueAlertRule(ProjectRepository projectRepository) {
        super(projectRepository);
    }

    @Override public String code() { return "ROLE_DEFAULT"; } // 复用现 typeCode
    @Override public String name() { return "质量事件 (MAJOR+ 缺陷 > 5)"; }
    @Override public String severity() { return "HIGH"; }
    @Override public double defaultThreshold() { return 5; }

    @Override
    protected List<AlertEvent> evaluateProject(Long projectId) {
        // v1.0 占位 — 等 defect 表接入
        return List.of();
    }
}
