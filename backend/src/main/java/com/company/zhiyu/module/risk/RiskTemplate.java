package com.company.zhiyu.module.risk;

import com.company.zhiyu.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 风险模板 (V4.26 数据库化 — 替换 InitiationAiWbsService.generateRisks switch 中的 40 个 addRisk).
 *
 * <p>对应 SQL: {@code V4.26__risk_rules.sql / risk_template}
 * <p>语义: 触发条件满足时, 把本条模板渲染成一个风险条目加入清单.
 *
 * <h3>触发条件 (全部 AND)</h3>
 * <ol>
 *   <li>{@link #bucketCode} 对应的桶被信号命中 (见 {@link RiskSignal}); 或 {@link #industryIn} 命中且无信号要求 (例如 AI_MODEL);</li>
 *   <li>{@link #industryIn} 为 NULL (任意行业), 或当前 industry 在 JSON 数组里 (例 {@code ["AI","AI_AGENT"]});</li>
 *   <li>{@link #sowContainsAny} 为 NULL (无额外门控), 或 SOW 含 JSON 数组里任一关键词 (例 {@code ["可预测","可追溯","幻觉"]});</li>
 *   <li>{@link #agentCode} 字段 (仅 AI_AGENT 桶使用): NULL 表示任意智能体, 否则只在 agent.code == agentCode 时触发;</li>
 *   <li>{@link #enabled} = true.</li>
 * </ol>
 *
 * <h3>占位符</h3>
 * <p>{@link #title} 中 {@code {agent_name}} 在运行时替换为 {@code agent.name} (坐席小结/语音质检/语音打标/财报分析).
 */
@Entity
@Table(name = "risk_template")
@Getter @Setter @NoArgsConstructor
public class RiskTemplate extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联 risk_bucket.code */
    @Column(name = "bucket_code", nullable = false, length = 50)
    private String bucketCode;

    /** 风险标题 (含 {agent_name} 占位符, 仅 AI_AGENT 智能体模板使用) */
    @Column(nullable = false, length = 255)
    private String title;

    /** 缓解建议 */
    @Column(columnDefinition = "text")
    private String suggestion;

    /** 风险等级 LOW/MEDIUM/HIGH/CRITICAL */
    @Column(length = 20)
    private String level;

    /** 概率 1-5 */
    @Column(nullable = false)
    private Integer probability = 3;

    /** 影响 1-5 */
    @Column(nullable = false)
    private Integer impact = 3;

    /**
     * 智能体 code (仅 AI_AGENT 桶使用): SUMMARY(坐席小结) / QA(语音质检) / TAG(语音打标) / FINREPT(财报分析).
     * 其他桶为 NULL.
     */
    @Column(name = "agent_code", length = 50)
    private String agentCode;

    /**
     * 行业白名单 JSON 数组: NULL=任意行业, 例 {@code ["AI","AI_AGENT"]}.
     * 格式: JSON 数组字符串, 解析失败时按 NULL 处理.
     */
    @Column(name = "industry_in", columnDefinition = "text")
    private String industryIn;

    /**
     * 额外 SOW 门控 JSON 数组: NULL=无要求, 例 {@code ["可预测","可追溯","幻觉"]}.
     * 语义: SOW 文本须含数组中至少一个关键词 (原 AI_HALLUCINATION 桶的"可预测/可追溯/幻觉"门控).
     */
    @Column(name = "sow_contains_any", columnDefinition = "text")
    private String sowContainsAny;

    /** 列表展示顺序 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** false 时,即使触发条件全满足也不生成 */
    @Column(nullable = false)
    private Boolean enabled = true;
}
