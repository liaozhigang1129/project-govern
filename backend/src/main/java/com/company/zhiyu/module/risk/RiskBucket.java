package com.company.zhiyu.module.risk;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 风险桶字典 (V4.26 数据库化 — 替换 generateRisks switch case 中的硬编码桶).
 *
 * <p>对应 SQL: {@code V4.26__risk_rules.sql / risk_bucket}
 * <p>每条 signal/template 都引用 {@link #code} (自然主键, 与旧硬编码 Map.ofEntries 等价).
 * <p>{@link #defaultLevel} / {@link #defaultImpact} 用于前端新建模板时的"建议值".
 */
@Entity
@Table(name = "risk_bucket")
@Getter @Setter @NoArgsConstructor
public class RiskBucket extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 风险桶 code, 例如 DATA_LABEL / COMPLIANCE / AI_HALLUCINATION (业务标识, 不可改) */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** 中文名 (UI 列表展示) */
    @Column(nullable = false, length = 100)
    private String name;

    /** 业务大类: 数据/模型/集成/团队/工期/预算/业务/合规/AI 等 */
    @Column(length = 50)
    private String category;

    /** 默认等级 HIGH/MEDIUM/CRITICAL/LOW (新建模板时建议值) */
    @Column(name = "default_level", length = 20)
    private String defaultLevel;

    /** 默认影响值 1-5 (新建模板时建议值) */
    @Column(name = "default_impact")
    private Integer defaultImpact;

    /** 列表展示顺序 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** false 时,即使有信号触发也不生成风险 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 备注: 业务来源/Step 版本/变更说明 */
    @Column(columnDefinition = "text")
    private String remark;
}
