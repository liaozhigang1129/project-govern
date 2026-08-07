package com.company.pmo.module.alert;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * F4: 预警规则定义
 *
 *  - code: 业务唯一 (RULE_BUDGET_90)
 *  - typeCode: 6 类 (BUDGET_EXCEED/HOURS_OVER/CONTRACT_BALANCE/PROJECT_STALE/ROLE_DEFAULT/PAYMENT_OVERDUE)
 *  - threshold: 阈值 (0.90 / 200 / 0.10 / 14 / 1 / 30)
 *  - comparison: GT / LT / EQ
 *  - severity: LOW / MEDIUM / HIGH / CRITICAL
 *  - targetFilter: 可选 (限定 project_id / user_id / department_id)
 *  - notifyEmails: 逗号分隔邮箱列表
 *  - webhookUrl: 可选 (钉钉 webhook)
 */
@Entity
@Table(name = "alert_rule", indexes = {
        @Index(name = "idx_alert_rule_type_code", columnList = "type_code"),
        @Index(name = "idx_alert_rule_enabled", columnList = "enabled")
})
@Getter @Setter @NoArgsConstructor
public class AlertRule extends SoftDeletableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "type_code", nullable = false, length = 32)
    private String typeCode;  // FK -> alert_type_def.code (字符串约束, 避免循环依赖)

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal threshold;

    @Column(nullable = false, length = 8)
    private String comparison;  // GT / LT / EQ

    @Column(nullable = false, length = 16)
    private String severity;  // LOW / MEDIUM / HIGH / CRITICAL

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "target_filter", length = 256)
    private String targetFilter;

    @Column(name = "notify_emails", length = 512)
    private String notifyEmails;

    @Column(name = "webhook_url", length = 512)
    private String webhookUrl;

    @Column(columnDefinition = "text")
    private String description;
}
