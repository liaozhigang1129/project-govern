package com.company.zhiyu.module.wbs.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * WBS 任务创建/更新请求 — 前端 el-tree 拖拽/右键"新增子任务"提交。
 * <p>parentId = null 表示顶层任务。
 * <p>predecessorIds 存的是同项目其他任务的 id, Service 层会在更新前校验"必须先于当前任务完成"。
 */
public record WbsTaskRequest(
        Long id,                                   // 更新时必填, 新建时为 null
        @NotNull Long projectId,
        Long parentId,                             // 顶层为 null
        @NotBlank @Size(max = 32) String wbsCode,
        @NotBlank @Size(max = 256) String name,
        @NotBlank @Pattern(regexp = "SUMMARY|EXECUTION|MILESTONE|DELIVERABLE",
                message = "taskType 必须是 SUMMARY / EXECUTION / MILESTONE / DELIVERABLE")
        String taskType,
        @NotBlank @Pattern(regexp = "NOT_STARTED|IN_PROGRESS|BLOCKED|COMPLETED|CANCELLED",
                message = "status 必须是 NOT_STARTED / IN_PROGRESS / BLOCKED / COMPLETED / CANCELLED")
        String status,
        Long ownerUserId,
        LocalDate planStartDate,
        LocalDate planEndDate,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal planHours,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal actualHours,
        @Min(0) @Max(100) Integer progressPct,
        @Min(1) @Max(10) Integer weight,
        Boolean critical,
        Boolean milestone,
        Long milestoneId,                          // 任务关联到外部里程碑时填写
        List<Long> predecessorIds,
        @Size(max = 2000) String deliverable,
        @Size(max = 2000) String remark
) {
    public String taskTypeOrDefault()    { return taskType    == null ? "EXECUTION"    : taskType; }
    public String statusOrDefault()      { return status      == null ? "NOT_STARTED"  : status; }
    public Integer progressOrDefault()   { return progressPct == null ? 0              : progressPct; }
    public Integer weightOrDefault()     { return weight      == null ? 1              : weight; }
    public boolean criticalOrDefault()   { return Boolean.TRUE.equals(critical); }
    public boolean milestoneOrDefault()  { return Boolean.TRUE.equals(milestone); }
}
