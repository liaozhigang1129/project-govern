package com.company.zhiyu.module.wbs.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 任务-人员分配请求。
 * <p>同一任务同一人员只一行(由 DB 唯一约束保证), 更新时传 id, 重复时 Service 层做 upsert。
 */
public record WbsAssignmentRequest(
        Long id,
        @NotNull Long wbsTaskId,
        @NotNull Long userId,
        @NotBlank @Pattern(regexp = "LEAD|DOER|REVIEWER|QA|OBSERVER",
                message = "role 必须是 LEAD / DOER / REVIEWER / QA / OBSERVER")
        String role,
        @NotNull @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal plannedHours,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal actualHours,
        LocalDate startDate,
        LocalDate endDate
) {
    public String roleOrDefault() { return role == null ? "DOER" : role; }
}
