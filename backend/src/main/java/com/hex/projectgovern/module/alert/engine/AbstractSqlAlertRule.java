package com.hex.projectgovern.module.alert.engine;

import com.hex.projectgovern.module.alert.AlertEvent;
import com.hex.projectgovern.module.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则抽象基类 (V5.1+ / WP-M5-02 / T-02)
 *
 * <p>提供:
 * <ul>
 *   <li>默认的 {@link #evaluate()} 实现:遍历所有 active project 调 {@link #evaluateProject(Long)}</li>
 *   <li>{@link #buildEvent(Long, BigDecimal, BigDecimal, String)} 工厂方法</li>
 * </ul>
 *
 * <p>子类只需实现:
 * <ul>
 *   <li>{@link #code()} / {@link #name()} / {@link #severity()} / {@link #defaultThreshold()}</li>
 *   <li>{@link #evaluateProject(Long)} 返回命中的事件 (空集合 = 无触发)</li>
 * </ul>
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractSqlAlertRule implements AlertRule {

    protected final ProjectRepository projectRepository;

    @Override
    public List<AlertEvent> evaluate() {
        List<AlertEvent> all = new ArrayList<>();
        var projects = projectRepository.findAll().stream()
                .filter(p -> !p.isDeleted())
                .toList();
        for (var p : projects) {
            try {
                all.addAll(evaluateProject(p.getId()));
            } catch (Exception e) {
                log.warn("[AlertRule.{}] project={} evaluate failed: {}",
                        code(), p.getId(), e.getMessage());
            }
        }
        return all;
    }

    @Override
    public List<AlertEvent> evaluate(Long projectId) {
        return evaluateProject(projectId);
    }

    /**
     * 子类实现:对单个 project 跑规则,返回命中的事件 (空 = 无触发)。
     */
    protected abstract List<AlertEvent> evaluateProject(Long projectId);

    /**
     * 事件工厂
     * @param projectId  触发项目
     * @param actualValue 实际值 (数值型,如工时/金额)
     * @param threshold   阈值
     * @param messageText 人类可读描述
     */
    protected AlertEvent buildEvent(Long projectId, BigDecimal actualValue,
                                     BigDecimal threshold, String messageText) {
        AlertEvent e = new AlertEvent();
        e.setRuleId(0L); // 调度器在入库前会查 rule.id 并覆盖
        e.setSeverity(severity());
        e.setMessage(messageText);
        e.setTargetType("PROJECT");
        e.setTargetId(projectId);
        e.setProjectId(projectId);
        e.setTargetLabel("project #" + projectId);
        e.setActualValue(actualValue);
        e.setThresholdValue(threshold);
        e.setStatus("NEW");
        e.setNotifyStatus("PENDING");
        e.setTriggeredAt(OffsetDateTime.now());
        return e;
    }
}
