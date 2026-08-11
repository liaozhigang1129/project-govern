package com.hex.projectgovern.module.alert.engine;

import com.hex.projectgovern.module.alert.AlertEvent;

import java.util.List;

/**
 * F4: 预警规则引擎抽象 (V5.1+ / WP-M5-02 / T-02)
 *
 * <p>每类预警规则(如 cost_overrun / resource_overload)实现此接口,
 * 调度器调用 {@link #evaluate(Long)} 拉所有目标 project 跑规则,
 * 命中的目标返回 {@link AlertEvent} 列表。
 *
 * <p>规则实现路径:
 * <ol>
 *   <li>实现 {@link AlertRule} 接口 (项目级扫描)</li>
 *   <li>或继承 {@link AbstractSqlAlertRule} (SQL 聚合类)</li>
 *   <li>声明 {@code @Component},注册到 {@link AlertRuleRegistry}</li>
 * </ol>
 */
public interface AlertRule {

    /** 规则编码,对应 alert_rule.code (如 COST_DIFF / BUDGET_EXCEED) */
    String code();

    /** 规则名称 (展示用) */
    String name();

    /** 严重度 (HIGH / MEDIUM / LOW / CRITICAL) */
    String severity();

    /** 默认阈值 (展示用,实际由规则实现类自己控制) */
    double defaultThreshold();

    /**
     * 调度入口:对所有目标评估一次 (M5 简化:按 project 维度扫)。
     * @return 命中的事件 (可空集合),调度器负责入库 + 去重
     */
    List<AlertEvent> evaluate();

    /**
     * 单目标评估:对指定 project 评估 (用于手动触发 / 重跑)。
     */
    List<AlertEvent> evaluate(Long projectId);
}
