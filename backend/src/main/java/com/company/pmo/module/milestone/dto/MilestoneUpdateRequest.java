package com.company.pmo.module.milestone.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 里程碑更新请求体(局部更新 — 甘特图拖拽改期用)
 * <ul>
 *   <li>所有字段都可选,null = 不改</li>
 *   <li>weight 范围 1-10</li>
 *   <li>status / actualDate 走专门的 /status 接口,这里只改 planDate/name/weight/owner/deliverable/remark</li>
 *   <li>拖拽改期场景:{planDate: "2025-09-15"} (只传一个字段)</li>
 * </ul>
 */
@Schema(description = "里程碑局部更新 — 甘特图拖拽改期用,所有字段可选 null=不改")
public record MilestoneUpdateRequest(

        @Schema(description = "里程碑名称,≤128 字(可选)")
        String name,

        @Schema(description = "计划完成日期(拖拽改期主要改这个,可选)")
        LocalDate planDate,

        @Schema(description = "项目内顺序,≥1(可选)")
        Integer sequence,

        @Schema(description = "阶段 id 1-7(可选,改阶段时传)")
        Long phaseId,

        @Min(value = 1, message = "weight 必须在 1-10 之间")
        @Max(value = 10, message = "weight 必须在 1-10 之间")
        @Schema(description = "权重 1-10(可选)", minimum = "1", maximum = "10")
        Integer weight,

        @Schema(description = "负责人 userId(可选)")
        Long ownerUserId,

        @Schema(description = "交付物(可选)")
        String deliverable,

        @Schema(description = "备注(可选)")
        String remark
) {
    /** 显式空构造 — 方便前端传部分字段时用 ObjectMapper 反序列化 */
    public static MilestoneUpdateRequest empty() {
        return new MilestoneUpdateRequest(null, null, null, null, null, null, null, null);
    }
}
