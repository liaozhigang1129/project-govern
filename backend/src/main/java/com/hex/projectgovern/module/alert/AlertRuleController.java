package com.hex.projectgovern.module.alert;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * F4: 预警规则 HTTP 入口
 *
 * <p>POST /api/alert/rules/seed/cost-diff
 * <ul>
 *   <li>幂等:已存在则跳过</li>
 *   <li>角色:PMO_ADMIN / ADMIN</li>
 *   <li>审计:@AuditLog</li>
 * </ul>
 *
 * @since V5.1 / WP-M4-03 / T-04
 */
@RestController
@RequestMapping("/alert/rules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN')")
public class AlertRuleController {

    private static final String COST_DIFF_RULE_CODE = "RULE_COST_DIFF_100";
    private static final String COST_DIFF_TYPE_CODE = "COST_DIFF";
    private static final String HIGH_SEVERITY = "HIGH";

    private final AlertRuleRepository ruleRepo;

    /**
     * 播种 COST_DIFF 规则 (idempotent)。
     * 配套 V5.1 migration 已自动种,但也可通过此端点手动触发或修复。
     */
    @PostMapping("/seed/cost-diff")
    @AuditLog(module = "ALERT_RULE", action = "SEED_COST_DIFF")
    @Operation(summary = "播种 COST_DIFF 告警规则 (幂等)")
    @Transactional
    public ApiResponse<Map<String, Object>> seedCostDiffRule() {
        Map<String, Object> data = new LinkedHashMap<>();

        var existing = ruleRepo.findByCodeAndDeletedFalse(COST_DIFF_RULE_CODE);
        if (existing.isPresent()) {
            data.put("created", false);
            data.put("ruleId", existing.get().getId());
            data.put("code", existing.get().getCode());
            return ApiResponse.ok(data);
        }

        AlertRule rule = new AlertRule();
        rule.setCode(COST_DIFF_RULE_CODE);
        rule.setName("成本对账差异 ≥ ¥100 警告");
        rule.setTypeCode(COST_DIFF_TYPE_CODE);
        rule.setThreshold(new java.math.BigDecimal("100.00"));
        rule.setComparison("GT");
        rule.setSeverity(HIGH_SEVERITY);
        rule.setEnabled(true);
        rule.setDescription("3-way match 中差异金额超过 ¥100 时触发 (24h 内同 project 同 diff_bucket 去重)");
        rule.setNotifyEmails("pmo@company.com,finance@company.com");
        AlertRule saved = ruleRepo.save(rule);

        data.put("created", true);
        data.put("ruleId", saved.getId());
        data.put("code", saved.getCode());
        return ApiResponse.ok(data);
    }

    @PostMapping("/{id}/toggle")
    @AuditLog(module = "ALERT_RULE", action = "TOGGLE")
    @Operation(summary = "切换告警规则启用状态")
    @Transactional
    public ApiResponse<Map<String, Object>> toggle(@PathVariable Long id) {
        Map<String, Object> data = new LinkedHashMap<>();
        var rule = ruleRepo.findByIdAndDeletedFalse(id).orElse(null);
        if (rule == null) {
            data.put("error", "RULE_NOT_FOUND");
            return ApiResponse.fail(404, "Rule not found: " + id);
        }
        rule.setEnabled(!Boolean.TRUE.equals(rule.getEnabled()));
        ruleRepo.save(rule);
        data.put("id", rule.getId());
        data.put("code", rule.getCode());
        data.put("enabled", rule.getEnabled());
        return ApiResponse.ok(data);
    }
}
