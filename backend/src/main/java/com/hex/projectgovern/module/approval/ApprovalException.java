package com.hex.projectgovern.module.approval;

/**
 * 审批引擎业务异常 (HTTP 400)
 * 与启动/状态/决策相关的业务规则违反
 */
public class ApprovalException extends RuntimeException {
    public ApprovalException(String message) { super(message); }
}