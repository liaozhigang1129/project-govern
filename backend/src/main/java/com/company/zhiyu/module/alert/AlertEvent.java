package com.company.zhiyu.module.alert;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * F4: 预警事件记录
 *
 *  - ruleId: 触发的规则
 *  - triggeredAt: 触发时间
 *  - severity: 严重程度 (与规则一致)
 *  - message: 人类可读描述
 *  - targetType/Id/Label: 触发目标 (PROJECT/USER/CONTRACT/INVOICE/SYSTEM)
 *  - actualValue: 实际值
 *  - thresholdValue: 阈值
 *  - status: NEW/ACKNOWLEDGED/RESOLVED/SUPPRESSED
 *  - notifyStatus: PENDING/SENT/FAILED/SKIPPED
 */
@Entity
@Table(name = "alert_event", indexes = {
        @Index(name = "idx_alert_event_rule_id", columnList = "rule_id"),
        @Index(name = "idx_alert_event_status", columnList = "status"),
        @Index(name = "idx_alert_event_severity", columnList = "severity"),
        @Index(name = "idx_alert_event_triggered_at", columnList = "triggered_at"),
        @Index(name = "idx_alert_event_target", columnList = "target_type, target_id")
})
@Getter @Setter @NoArgsConstructor
public class AlertEvent extends SoftDeletableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_label", length = 256)
    private String targetLabel;

    @Column(name = "actual_value", precision = 14, scale = 4)
    private BigDecimal actualValue;

    @Column(name = "threshold_value", precision = 14, scale = 4)
    private BigDecimal thresholdValue;

    @Column(nullable = false, length = 16)
    private String status = "NEW";

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "notify_status", nullable = false, length = 16)
    private String notifyStatus = "PENDING";

    @Column(name = "notify_sent_at")
    private OffsetDateTime notifySentAt;
}
