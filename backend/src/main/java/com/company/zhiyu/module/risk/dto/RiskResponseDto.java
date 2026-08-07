package com.company.zhiyu.module.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 风险应对行动请求 / 响应 (P4)。
 */
public final class RiskResponseDto {

    private RiskResponseDto() {}

    public record Request(
            Long id,
            @NotBlank @Size(max = 256) String action,
            Long ownerUserId,
            LocalDate dueDate,
            String status,    // PLANNED / IN_PROGRESS / DONE / CANCELLED
            String note
    ) {
        public String statusOrDefault() {
            return status == null || status.isBlank() ? "PLANNED" : status;
        }
    }

    public record Item(
            Long id,
            Long riskId,
            String action,
            Long ownerUserId,
            String ownerName,
            LocalDate dueDate,
            Instant completedAt,
            String status,
            String note,
            Instant createdAt
    ) {}
}
