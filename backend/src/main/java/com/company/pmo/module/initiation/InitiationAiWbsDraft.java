package com.company.pmo.module.initiation;

import com.company.pmo.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * AI WBS 转化助手的输出暂存(Step 2 写入,Step 3 用户确认后写 wbs_task 并设置 applied_at)。
 * <p>对齐 V3.0 {@code initiation_ai_wbs_draft} 表。
 * <p>draftJson 形如:
 * <pre>
 * {
 *   "milestones":   [{"code":"M1","name":"...","targetWeek":2,"workPackageCodes":["1.1","1.2"]}],
 *   "workPackages": [{"wbsCode":"1.1","name":"...","estimateHours":80,"ownerRole":"FR","deliverable":"..."}],
 *   "risks":        [{"title":"...","probability":3,"impact":4,"level":"HIGH","suggestion":"..."}]
 * }
 * </pre>
 */
@Entity
@Table(name = "initiation_ai_wbs_draft")
@Getter @Setter @NoArgsConstructor
public class InitiationAiWbsDraft extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "initiation_id", nullable = false)
    private Long initiationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_json", nullable = false, columnDefinition = "json")
    private String draftJson;

    @Column(name = "granularity_weeks", nullable = false)
    private Integer granularityWeeks = 2;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "applied_by")
    private Long appliedBy;
}
