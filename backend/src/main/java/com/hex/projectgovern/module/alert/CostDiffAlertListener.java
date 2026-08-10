package com.hex.projectgovern.module.alert;

import com.hex.projectgovern.module.finance.ReconciliationService.CostDiffDetectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * F4 + F3: COST_DIFF 告警规则监听器 (V5.1 / WP-M4-03 / T-04)
 *
 * <p>订阅 {@link CostDiffDetectedEvent},为项目创建一条 alert_event。
 * <ul>
 *   <li>规则:RULE_COST_DIFF_100(diff_amount ≥ ¥100,HIGH severity)</li>
 *   <li>去重:24h 内同 (rule, project) 已有 NEW/ACKNOWLEDGED 事件则跳过</li>
 *   <li>target:PROJECT, target_id = project_id</li>
 *   <li>失败隔离:try-catch warn log</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CostDiffAlertListener {

    private static final String RULE_CODE = "RULE_COST_DIFF_100";
    private static final Duration DEDUP_WINDOW = Duration.ofHours(24);

    private final AlertRuleRepository ruleRepo;
    private final AlertEventRepository eventRepo;

    @Async
    @EventListener
    @Transactional
    public void onCostDiff(CostDiffDetectedEvent event) {
        try {
            Optional<AlertRule> ruleOpt = ruleRepo.findByCodeAndDeletedFalse(RULE_CODE);
            if (ruleOpt.isEmpty()) {
                log.warn("[CostDiffAlert] rule {} not found, 请先 POST /api/alert/rules/seed", RULE_CODE);
                return;
            }
            AlertRule rule = ruleOpt.get();
            if (!Boolean.TRUE.equals(rule.getEnabled())) {
                log.debug("[CostDiffAlert] rule {} disabled, skip", RULE_CODE);
                return;
            }

            // 24h 去重
            OffsetDateTime since = OffsetDateTime.now().minus(DEDUP_WINDOW);
            var recent = eventRepo.findRecentOpen(rule.getId(), event.projectId(), since);
            if (!recent.isEmpty()) {
                log.debug("[CostDiffAlert] project={} 已有 24h 内未解决事件,跳过 (existing id={})",
                        event.projectId(), recent.get(0).getId());
                return;
            }

            // 创建 alert_event
            AlertEvent ae = new AlertEvent();
            ae.setRuleId(rule.getId());
            ae.setTriggeredAt(OffsetDateTime.now());
            ae.setSeverity(rule.getSeverity());
            ae.setMessage(String.format(
                "项目 #%d 财务-成本对账发现 %d 个对账桶异常,触发 COST_DIFF 规则 (阈值 ¥%s)",
                event.projectId(), event.affectedBuckets(),
                rule.getThreshold().toPlainString()));
            ae.setTargetType("PROJECT");
            ae.setTargetId(event.projectId());
            ae.setProjectId(event.projectId());
            ae.setTargetLabel("project #" + event.projectId());
            ae.setActualValue(BigDecimal.valueOf(event.affectedBuckets()));
            ae.setThresholdValue(rule.getThreshold());
            ae.setStatus("NEW");
            ae.setNotifyStatus("PENDING");

            AlertEvent saved = eventRepo.save(ae);
            log.info("[CostDiffAlert] created event id={} projectId={} rule={}",
                    saved.getId(), event.projectId(), RULE_CODE);

            // 通知发送留 alert 模块现有 dispatcher (此处仅落库,后续 P-04 dispatcher 扩展)
        } catch (Exception e) {
            log.warn("[CostDiffAlert] failed project={} err={}", event.projectId(), e.getMessage(), e);
        }
    }
}
