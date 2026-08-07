package com.company.pmo.module.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DND 勿扰时段 DTO(P2 #2)。
 *
 * 时间格式约束:HH:mm 24h(MVP)。
 */
public final class UserImQuietHoursDtos {

    private UserImQuietHoursDtos() {}

    public record View(
            Long id,
            Long userId,
            String startTime,
            String endTime,
            String timezone,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateReq(
            @NotNull Long userId,
            @NotBlank @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                    message = "startTime must be HH:mm (24h)") String startTime,
            @NotBlank @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                    message = "endTime must be HH:mm (24h)") String endTime,
            @Size(max = 64) String timezone
    ) {}

    public record UpdateReq(
            @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                    message = "startTime must be HH:mm (24h)") String startTime,
            @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$",
                    message = "endTime must be HH:mm (24h)") String endTime,
            @Size(max = 64) String timezone,
            Boolean enabled
    ) {}
}
