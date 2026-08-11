package com.hex.projectgovern.module.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class ReportingDtos {
    public record DatasetRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String domain,
        String sourceTable,
        String sqlTemplate,
        String refreshPolicy,
        String description
    ) {}
    public record DatasetResponse(
        Long id, String code, String name, String domain,
        String sourceTable, String status, String refreshPolicy,
        String description
    ) {}
    public record DatasetFieldRequest(
        Long datasetId,
        @NotBlank String fieldName,
        @NotBlank String displayName,
        @NotBlank String fieldType,
        @NotBlank String dataType,
        String aggFunc,
        String formula,
        String dimRole,
        Integer sortOrder
    ) {}
    public record DatasetFieldResponse(
        Long id, String fieldName, String displayName,
        String fieldType, String dataType, String aggFunc, String dimRole
    ) {}

    public record ReportTemplateRequest(
        @NotBlank String code,
        @NotBlank String category,
        @NotBlank String name,
        Long datasetId,
        String format,
        String defaultFilters,
        String layout,
        String scheduleCron,
        String description
    ) {}
    public record ReportTemplateResponse(
        Long id, String code, String category, String name,
        String format, String status, String scheduleCron
    ) {}
    public record ReportRenderRequest(
        Long templateId,
        String format,
        String params
    ) {}

    public record SubscriptionRequest(
        @NotBlank String code,
        Long userId,
        Long templateId,
        Long dashboardId,
        @NotBlank String channelSet,
        @NotBlank String cron,
        String recipients,
        String params
    ) {}
    public record SubscriptionResponse(
        Long id, String code, Long userId, String channelSet, String cron, String status
    ) {}
}
