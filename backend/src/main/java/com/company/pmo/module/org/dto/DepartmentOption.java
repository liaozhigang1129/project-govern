package com.company.pmo.module.org.dto;

import com.company.pmo.module.org.Department;

import java.time.Instant;

/** 部门简表 (其他模块下拉用) */
public record DepartmentOption(Long id, String code, String name, Long parentId, boolean enabled) {
    public static DepartmentOption from(Department d) {
        return new DepartmentOption(d.getId(), d.getCode(), d.getName(), d.getParentId(), d.isEnabled());
    }
}
