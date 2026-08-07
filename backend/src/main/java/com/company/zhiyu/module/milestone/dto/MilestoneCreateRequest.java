package com.company.zhiyu.module.milestone.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 新建里程碑请求体
 * <p>weight 必须在 1-10 之间(Milestone 实体做兜底校验)
 * status / id 都不收,后端用默认 PENDING
 */
@Schema(description = "新建里程碑请求体,status 后端强制 PENDING,weight 必填 1-10")
public record MilestoneCreateRequest(
        @NotNull @Schema(description = "所属项目 id", example = "1")
        Long projectId,

        @NotBlank @Schema(description = "里程碑名称,≤128 字", example = "需求评审")
        String name,

        @NotNull @Min(1) @Schema(description = "项目内顺序,≥1", example = "1")
        Integer sequence,

        @NotNull @Schema(description = "阶段 id (1-7):立项/需求/设计/开发/测试/上线运维/维保", example = "2")
        Long phaseId,

        @NotNull @Schema(description = "计划完成日期", example = "2025-09-30")
        LocalDate planDate,

        @NotNull @Min(value = 1, message = "weight 必须在 1-10 之间")
        @Max(value = 10, message = "weight 必须在 1-10 之间")
        @Schema(description = "权重,影响项目总进度百分比,范围 1-10", example = "5", minimum = "1", maximum = "10")
        Integer weight,

        @Schema(description = "负责人 userId(可选)", example = "2")
        Long ownerUserId,

        @Schema(description = "交付物描述(可选)")
        String deliverable,

        @Schema(description = "备注(可选)")
        String remark
) {}
