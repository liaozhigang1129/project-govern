package com.hex.projectgovern.module.org.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * V4.12: 批量操作请求
 *  - bulk-enabled:  enabled 必须传
 *  - bulk-department:  departmentId 必须传
 *  - bulk-unlock: enabled/departmentId 忽略
 */
public record BulkUserRequest(
        @NotEmpty @Size(max = 500) List<Long> ids,
        Boolean enabled,             // 启停
        Long departmentId            // 调部门
) {
    public boolean isEnabledRequest() {
        return enabled != null;
    }
    public boolean isDepartmentRequest() {
        return departmentId != null;
    }
}