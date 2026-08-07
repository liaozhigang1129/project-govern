package com.company.zhiyu.module.risk;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 风险信号词典 (V4.26 数据库化 — 替换 SowExtractor.RISK_SIGNAL_TO_BUCKET 硬编码).
 *
 * <p>对应 SQL: {@code V4.26__risk_rules.sql / risk_signal}
 * <p>语义: SOW 文本 {@code contains(keyword)} 时,把 {@link #bucketCode} 桶纳入风险清单 (evidence 加 keyword).
 * <p>{@link #industry} = NULL 时所有行业都触发 (与原代码兼容);非 NULL 时仅限定行业触发.
 *
 * <p>注: industry 字段当前全部为 NULL,保留原"信号无视行业"语义.
 */
@Entity
@Table(name = "risk_signal", uniqueConstraints = {
        @UniqueConstraint(name = "uk_risk_signal_bucket_kw", columnNames = {"bucket_code", "keyword"})
})
@Getter @Setter @NoArgsConstructor
public class RiskSignal extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联 risk_bucket.code */
    @Column(name = "bucket_code", nullable = false, length = 50)
    private String bucketCode;

    /** SOW 中关键词 (contains 匹配); 不区分大小写 (原代码 Map.ofEntries 即 case-sensitive) */
    @Column(nullable = false, length = 100)
    private String keyword;

    /** NULL=通用 (任何行业触发); 否则限定行业 (当前数据全 NULL, 与原硬编码兼容) */
    @Column(length = 50)
    private String industry;

    /** 触发权重 (同一桶多条信号时, 用于 evidence 排序/聚合, 当前实现 = 1) */
    @Column(nullable = false)
    private Integer weight = 1;

    /** false 时,即使 SOW 含 keyword 也不触发 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 备注: 来源 Step/业务说明 */
    @Column(columnDefinition = "text")
    private String remark;
}
