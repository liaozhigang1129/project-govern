package com.company.zhiyu.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记在 Controller 方法上,自动写入审计日志(operation_log)。
 *
 * <p>示例:
 * <pre>{@code
 *   @AuditLog(module = "USER", action = "CREATE")
 *   @PostMapping("/users")
 *   public ApiResponse<UserDto> create(@RequestBody CreateUserReq req) { ... }
 * }</pre>
 *
 * <p>约束:
 * <ul>
 *   <li>module 必填,32 字符以内,使用大写 + 下划线:USER / PROJECT / MILESTONE / INITIATION / DICTIONARY / DEPT / ROLE</li>
 *   <li>action 必填,32 字符以内,使用动词:CREATE / UPDATE / DELETE / LOGIN / LOGOUT / APPROVE / REJECT / SUPPLEMENT</li>
 *   <li>只对写操作(POST/PUT/PATCH/DELETE)贴,GET 读操作不审计(数据量大)</li>
 *   <li>方法必须返回 ApiResponse&lt;...&gt;,切面从 data.id 提取 resource_id</li>
 * </ul>
 *
 * @see OperationLogAspect
 * @since 2026-Q1 P1.5-d
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 资源类型(模块名,32 字符以内,大写) */
    String module();

    /** 操作动作(32 字符以内) */
    String action();

    /**
     * 是否从返回值 data.id 提取 resource_id(默认 true)。
     * 批量删除等多目标操作设为 false。
     */
    boolean extractResourceId() default true;
}
