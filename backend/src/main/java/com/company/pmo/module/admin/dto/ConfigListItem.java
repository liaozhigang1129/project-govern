package com.company.pmo.module.admin.dto;

import com.company.pmo.module.admin.SystemConfig;
import com.company.pmo.module.admin.SystemConfigValueType;

import java.time.Instant;

public record ConfigListItem(
        Long id,
        String configKey,
        String configValue,
        String defaultValue,
        SystemConfigValueType valueType,
        String options,
        String configGroup,
        String description,
        int sortOrder,
        boolean isDefault,
        Instant updatedAt,
        String updatedBy
) {
    public static ConfigListItem from(SystemConfig c) {
        return new ConfigListItem(
                c.getId(), c.getConfigKey(), c.getConfigValue(), c.getDefaultValue(),
                c.getValueType(), c.getOptions(), c.getConfigGroup(), c.getDescription(),
                c.getSortOrder(),
                c.getConfigValue() != null && c.getConfigValue().equals(c.getDefaultValue()),
                c.getUpdatedAt(), c.getUpdatedBy()
        );
    }
}
