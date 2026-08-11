package com.hex.projectgovern.module.alert.engine.impl;

import com.hex.projectgovern.module.alert.AlertEvent;
import com.hex.projectgovern.module.alert.engine.AbstractSqlAlertRule;
import com.hex.projectgovern.module.project.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规则 2/6: 进度落后 (schedule_delay)
 *
 * <p>判定:实际完成 % < 计划完成 % - 10% → 触发 MEDIUM 告警
 *
 * <p>v1.0 占位:留给 milestone 模块接入后实现。
 *
 * @since V5.1+ / WP-M5-02 / T-03
 */
@Component
@Slf4j
public class ScheduleDelayAlertRule extends AbstractSqlAlertRule {

    public ScheduleDelayAlertRule(ProjectRepository projectRepository) {
        super(projectRepository);
    }

    @Override public String code() { return "PROJECT_STALE"; }
    @Override public String name() { return "项目进度落后 10%+"; }
    @Override public String severity() { return "MEDIUM"; }
    @Override public double defaultThreshold() { return 10; }

    @Override
    protected List<AlertEvent> evaluateProject(Long projectId) {
        // v1.0 占位 — 等 milestone.completion_pct + plan_pct 接入
        return List.of();
    }
}
