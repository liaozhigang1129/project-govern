package com.hex.projectgovern.module.approval;

/**
 * 审批人解析接口 (按 role_code + 业务上下文返回 userId)
 * 默认实现 {@link DefaultApproverResolver} 接现有 ApproverResolution 工具类
 */
public interface ApproverResolver {
    /**
     * @param roleCode     DEPT_LEAD/PMO_ADMIN/EXEC/DYNAMIC_ROLE
     * @param departmentId 业务部门
     * @param applicantId  申请人(防止自审,可选项)
     * @return 审批人 userId;null=未解析到
     */
    Long resolve(String roleCode, Long departmentId, Long applicantId);
}