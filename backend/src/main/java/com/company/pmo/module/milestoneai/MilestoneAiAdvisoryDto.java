package com.company.pmo.module.milestoneai;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 建议详情 DTO (含 5 维信号明细) */
public record MilestoneAiAdvisoryDto(
        Long id,
        Long projectId,
        Long milestoneId,
        Long phaseId,
        String phaseCode,
        String phaseName,
        String milestoneName,
        LocalDate milestonePlanDate,
        String milestoneStatusCode,
        String severity,
        BigDecimal score,
        BigDecimal confidence,
        BigDecimal signalOverdue,
        BigDecimal signalSpi,
        BigDecimal signalPhaseLag,
        BigDecimal signalVelocity,
        BigDecimal signalHistorical,
        JsonNode reasons,
        JsonNode suggestions,
        String category,
        Integer suggestedProbability,
        Integer suggestedImpact,
        String status,
        String modelVersion,
        Instant decidedAt,
        Instant appliedAt,
        Long appliedBy,
        Long appliedRiskId,
        Instant mlPredictedAt,
        String mlSeverity,
        java.math.BigDecimal mlConfidence,
        String llmSummary,
        String feedbackType,
        Instant feedbackAt,
        String feedbackNote,
        Instant rejectedAt,
        Long rejectedBy,
        String rejectReason,
        String fingerprint,
        Instant createdAt,
        Instant updatedAt,
        List<MilestoneAiSignalDto> signals
) {}

record MilestoneAiSignalDto(
        Long id,
        String signalType,
        java.math.BigDecimal intensity,
        java.math.BigDecimal weight,
        java.math.BigDecimal score,
        String description,
        boolean missing
) {}
