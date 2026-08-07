package com.company.zhiyu.module.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * UserIMBinding DTO(P2-A)。
 */
public final class UserImBindingDtos {

    private UserImBindingDtos() {}

    public record View(
            Long id,
            Long userId,
            String channel,
            String externalUserId,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateReq(
            @NotNull  Long userId,
            @NotBlank @Size(max = 32)  String channel,
            @NotBlank @Size(max = 128) String externalUserId
    ) {}

    public record UpdateReq(
            @Size(max = 128) String externalUserId,
            Boolean enabled
    ) {}
}
