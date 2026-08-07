package com.hex.projectgovern.module.risk;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 风险变更历史(审计追踪)。
 * <p>对齐 V2.6 {@code risk_history} 表。
 * <p>记录: 状态变更 / 分数变更 / 责任人变更 / 评论 / 应对行动变更。
 * <p>旧/新值用 TEXT 存, JSON 序列化由 Service 层负责。
 */
@Entity
@Table(name = "risk_history")
@Getter @Setter @NoArgsConstructor
public class RiskHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    @Column(nullable = false, length = 32)
    private String action;     // CREATED / STATUS_CHANGED / SCORE_CHANGED / OWNER_CHANGED / LEVEL_CHANGED / COMMENTED / RESPONSE_ADDED / RESPONSE_DONE

    @Column(name = "field_name", length = 64)
    private String fieldName;  // 哪个字段变了 (e.g. status / score / owner_user_id)

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
