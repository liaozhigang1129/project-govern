package com.company.zhiyu.module.timesheet.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TimesheetDtos {

    /** 新建空周报(自动算 weekEnd = weekStart + 6) */
    @Data
    public static class CreateRequest {
        @NotNull private Long userId;
        @NotNull private LocalDate weekStart;  // 必须是周一
    }

    /** 单条明细 DTO(用于批量 upsert) */
    @Data
    public static class EntryRequest {
        private Long id;                       // null = 新建,有值 = 更新
        @NotNull private LocalDate workDate;
        @NotNull private Long projectId;
        private Long milestoneId;              // 可空
        @NotNull @DecimalMin("0.0") @DecimalMax("24.0") private BigDecimal hours;
        @Size(max = 500) private String description;
    }

    /** 批量替换明细 */
    @Data
    public static class EntriesRequest {
        @NotNull
        @Size(min = 1, max = 7)                  // 一周最多 7 天
        private java.util.List<EntryRequest> entries;
    }

    /** 提交 */
    @Data
    public static class SubmitRequest {
        @Size(max = 1000) private String submitterNote;
    }

    /** 审批 */
    @Data
    public static class ApproveRequest {
        @Size(max = 500) private String comment;
    }

    /** 批量批准(只传 ids,逐条调 service.approve) */
    @Data
    public static class BatchApproveRequest {
        @NotNull @Size(min = 1, max = 200) private java.util.List<Long> ids;
    }
}
