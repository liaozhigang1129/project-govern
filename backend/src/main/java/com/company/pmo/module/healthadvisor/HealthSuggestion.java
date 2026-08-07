package com.company.pmo.module.healthadvisor;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * 单个项目的健康度建议结果。
 *
 * 字段:
 *  - projectId  目标项目
 *  - currentCode / currentName  当前的健康度(可能 null,从未手工指定过)
 *  - suggestedCode / suggestedName  算法跑出来的建议(GREEN/YELLOW/RED)
 *  - overdueDays  超 plan_end_date 的天数(<=0 表示未超期)
 *  - milestoneCompletionPct  已完成里程碑按权重加权的完成率 0-100
 *  - reasons  触发该建议的可读原因(便于前端展示)
 *  - decidedAt  跑批时间
 */
@Value
@Builder
public class HealthSuggestion {
    Long projectId;
    String projectCode;
    String projectName;
    String currentCode;
    String currentName;
    String suggestedCode;
    String suggestedName;
    int overdueDays;
    int milestoneCompletionPct;
    List<String> reasons;
    Instant decidedAt;
}
