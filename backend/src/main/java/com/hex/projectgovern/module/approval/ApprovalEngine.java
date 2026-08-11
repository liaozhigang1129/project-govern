package com.hex.projectgovern.module.approval;

import java.util.Map;

/**
 * 通用审批工作流引擎接口
 *
 * <p>三种调用:
 * <ul>
 *   <li>{@link #start}: 业务实体进入 NEW → 立即创建实例并推进到 step 1
 *   <li>{@link #decide}: 当前 step 审批人做决策 → 推进/终止
 *   <li>{@link #cancel}: 申请人撤回或业务方作废
 * </ul>
 *
 * <p>事务语义: start/decide/cancel 均需在调用方事务内运行;
 * 跨事务副作用(发通知/写日志)通过 ApplicationEventPublisher 异步触发。
 */
public interface ApprovalEngine {

    /**
     * 启动流程: 创建 approval_flow_instance + 写入 STARTED 动作 + 推进到 step 1
     *
     * @param kind         业务类型 init/timesheet/risk/budget
     * @param flowCode     流程编码 (kind 内唯一)
     * @param bizId        业务主键
     * @param bizCode      业务单据号
     * @param applicantId  申请人
     * @param departmentId 业务部门(用于 DEPT_LEAD 解析)
     * @param bizPayload   业务 payload JSON (供 skip_when 解析)
     * @return 启动后的实例
     */
    ApprovalFlowInstance start(String kind, String flowCode, Long bizId, String bizCode,
                                Long applicantId, Long departmentId, String bizPayload);

    /**
     * 当前 step 审批人做决策
     *
     * @param instanceId 流程实例
     * @param approverId 当前 step 审批人
     * @param decision   APPROVED/REJECTED/SUPPLEMENT
     * @param comment    备注
     * @return 推进后的实例
     */
    ApprovalFlowInstance decide(Long instanceId, Long approverId, ApprovalDecision decision, String comment);

    /**
     * 申请人撤回 (业务侧调用,流程实例置 CANCELLED)
     */
    ApprovalFlowInstance cancel(Long instanceId, Long applicantId);

    /**
     * 查询: 业务实体当前审批状态 (kind + bizId)
     */
    ApprovalFlowInstance findByBiz(String kind, Long bizId);

    /**
     * 解析当前 step 应通知的审批人 (供通知中心使用)
     *
     * @return 审批人 userId 列表;null/empty = 当前无可解析审批人
     */
    Long resolveCurrentStepApprover(Long instanceId);

    /**
     * 流程是否终态
     */
    boolean isTerminal(Long instanceId);

    /**
     * 实例详情(供前端展示): 当前 step + 决策历史
     */
    Map<String, Object> describe(Long instanceId);
}