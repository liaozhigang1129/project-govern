package com.company.pmo.module.project.dto;

import com.company.pmo.module.milestone.dto.MilestoneResponse;

import java.util.List;

/**
 * 项目详情页聚合响应 — 一次请求拿到详情 + 里程碑列表 + 加权进度
 * <p>前端 ProjectDetail.vue 一个 onMounted 就拿全,避免 3 次瀑布请求
 */
public record ProjectOverviewResponse(
        ProjectDetailResponse project,
        List<MilestoneResponse> milestones,
        int progressPct
) {}
