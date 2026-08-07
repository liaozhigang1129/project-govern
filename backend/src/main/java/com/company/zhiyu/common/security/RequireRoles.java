package com.company.zhiyu.common.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色快捷注解
 * - 减少重复书写 @PreAuthorize("hasAnyRole('A','B')")
 * - 后端 role code 与 seed 数据对齐:PMO_ADMIN / DEPT_LEAD / PM / VIEWER / EXEC / ADMIN
 *
 * 用法:
 *   @RequireRoles.Read              // 任意已登录
 *   @RequireRoles.Operate          // 写操作:PM/PMO/EXEC
 *   @RequireRoles.Admin            // 管理员
 *   @RequireRoles.Approve          // 审批:DEPT_LEAD/PMO_ADMIN/EXEC
 *   @RequireRoles.Dict             // 字典:全员
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("isAuthenticated()")
public @interface RequireRoles {
    String[] value() default {};

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("isAuthenticated()")
    @interface Read {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyRole('PM','PMO_ADMIN','ADMIN','EXEC')")
    @interface Operate {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN')")
    @interface Admin {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyRole('DEPT_LEAD','PMO_ADMIN','ADMIN','EXEC')")
    @interface Approve {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyRole('VIEWER','PM','DEPT_LEAD','PMO_ADMIN','ADMIN','EXEC')")
    @interface Dict {}
}