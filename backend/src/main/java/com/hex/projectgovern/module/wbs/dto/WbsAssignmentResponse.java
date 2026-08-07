package com.hex.projectgovern.module.wbs.dto;

import com.hex.projectgovern.module.wbs.WbsAssignment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record WbsAssignmentResponse(
        Long id,
        Long wbsTaskId,
        Long userId,
        String role,
        BigDecimal plannedHours,
        BigDecimal actualHours,
        LocalDate startDate,
        LocalDate endDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static WbsAssignmentResponse from(WbsAssignment a) {
        return new WbsAssignmentResponse(
                a.getId(), a.getWbsTaskId(), a.getUserId(), a.getRole(),
                a.getPlannedHours(), a.getActualHours(),
                a.getStartDate(), a.getEndDate(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
