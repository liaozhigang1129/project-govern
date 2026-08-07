package com.hex.projectgovern.module.milestone.dto;

import java.time.LocalDate;

/**
 * 里程碑分析查询参数 (主视图 + 下钻, V3.1 改:加 phaseId)
 *
 * <p>字段语义:
 * <ul>
 *   <li>scope — company/bu/pl, 主视图+下钻都要传</li>
 *   <li>period — this_week/this_month/next_week/next_month/custom, 与 from/to 互斥</li>
 *   <li>from / to — period=custom 时必填</li>
 *   <li>buId / plId — 仅 scope=bu 或 scope=pl 时必填</li>
 *   <li>phaseId — 主视图桶ID (1-7); 下钻用限定到具体 phase</li>
 *   <li>statusCode — 4 status (PENDING/IN_PROGRESS/COMPLETED/DELAYED), null = 全部</li>
 *   <li>milestoneId / milestoneName — 仅下钻时传, 限定到具体里程碑</li>
 * </ul>
 */
public record MilestoneAnalysisQuery(
        String scope,
        String period,
        LocalDate from,
        LocalDate to,
        Long buId,
        Long plId,
        Long phaseId,         // V3.1: 主视图桶 / 下钻过滤
        String statusCode,
        Long milestoneId,
        String milestoneName
) {}
