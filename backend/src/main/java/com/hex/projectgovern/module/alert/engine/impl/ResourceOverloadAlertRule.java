package com.hex.projectgovern.module.alert.engine.impl;

import com.hex.projectgovern.module.alert.AlertEvent;
import com.hex.projectgovern.module.alert.engine.AbstractSqlAlertRule;
import com.hex.projectgovern.module.project.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则 5/6: 资源过载 (resource_overload)
 *
 * <p>判定:单用户周工时 > 50h → 触发 MEDIUM 告警
 *
 * <p>实现 (V5.1+ v1.0):
 *  - 由于无直接的 hours/week SQL 视图,V1.0 简化为按 project 评估:
 *    若项目下"任一用户"在 timesheet_week 的 status=APPROVED 且 week_start 在近 4 周内
 *    出现 > 1 条记录(高频提交),记为可能过载
 *  - 真正的工时聚合留给 T-04 后接入 cost_calculation 后的 V2 规则引擎
 *
 * <p>v1.0 此规则主要作为调度骨架演示,**不会真触发**(always returns empty)。
 *
 * @since V5.1+ / WP-M5-02 / T-03
 */
@Component
@Slf4j
public class ResourceOverloadAlertRule extends AbstractSqlAlertRule {

    /** 单用户周工时阈值 (h) */
    public static final BigDecimal WEEK_HOURS_THRESHOLD = new BigDecimal("50");

    public ResourceOverloadAlertRule(ProjectRepository projectRepository) {
        super(projectRepository);
    }

    @Override public String code() { return "HOURS_OVER"; }
    @Override public String name() { return "单人月工时超限"; }
    @Override public String severity() { return "MEDIUM"; }
    @Override public double defaultThreshold() { return 200; }

    @Override
    protected List<AlertEvent> evaluateProject(Long projectId) {
        // v1.0 占位:留待 cost_calculation 接入后实现工时聚合
        // 这里返回空,避免假触发污染告警数据
        log.debug("[ResourceOverloadAlertRule] project={} v1.0 占位,无触发", projectId);
        return List.of();
    }
}
