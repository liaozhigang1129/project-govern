-- ============================================================
-- V6.0 通用审批工作流引擎 (MySQL 版)
-- 与 PostgreSQL 版 V6.0__approval_engine.sql 同构
-- 取代业务侧手写状态机 (立项审批 / 工时审批 / 风险升级)
-- ============================================================

-- 1) 流程定义 (按 kind 维度区分: initiation/timesheet/risk/budget 等)
CREATE TABLE approval_flow_def (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kind VARCHAR(32) NOT NULL COMMENT '业务类型 init/timesheet/risk/budget',
    code VARCHAR(64) NOT NULL COMMENT '流程编码,业务内唯一',
    name VARCHAR(128) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_approval_flow_def_kind_code_ver (kind, code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程定义';

-- 2) 流程步骤 (按 step_no 排序,role_code 决定审批人解析)
CREATE TABLE approval_flow_step (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    flow_def_id BIGINT NOT NULL,
    step_no INT NOT NULL COMMENT '从 1 开始递增',
    role_code VARCHAR(64) NOT NULL COMMENT 'DEPT_LEAD/PMO_ADMIN/EXEC/DYNAMIC_ROLE',
    name VARCHAR(128) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'false=咨询节点(不阻塞)',
    auto_approve_when BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'true=无审批人时自动通过(兜底)',
    skip_when VARCHAR(256) NULL COMMENT '跳过条件表达式(简单 KEY=VALUE,如 amount<1000)',
    timeout_hours INT NULL COMMENT '超时升级阈值,null=不超时',
    UNIQUE KEY uk_approval_flow_step_def_no (flow_def_id, step_no),
    CONSTRAINT fk_approval_flow_step_def FOREIGN KEY (flow_def_id) REFERENCES approval_flow_def(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程步骤';

-- 3) 流程实例 (一个业务实体 = 一个实例)
CREATE TABLE approval_flow_instance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    flow_def_id BIGINT NOT NULL,
    kind VARCHAR(32) NOT NULL COMMENT '冗余便于查询',
    biz_id BIGINT NOT NULL COMMENT '业务主键 (initiation_id / timesheet_id 等)',
    biz_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL COMMENT 'INITIAL/PENDING/APPROVED/REJECTED/CANCELLED/SUPPLEMENT',
    current_step_no INT NOT NULL DEFAULT 0,
    applicant_id BIGINT NOT NULL,
    department_id BIGINT NULL,
    biz_payload TEXT NULL COMMENT 'JSON: 决策金额等供 skip_when 解析',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    UNIQUE KEY uk_approval_flow_instance_kind_biz (kind, biz_id),
    KEY idx_approval_flow_instance_status (status, created_at),
    KEY idx_approval_flow_instance_applicant (applicant_id, status),
    CONSTRAINT fk_approval_flow_instance_def FOREIGN KEY (flow_def_id) REFERENCES approval_flow_def(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程实例';

-- 4) 流程动作 (审批历史,等效于现有 approval_record 但更通用)
CREATE TABLE approval_flow_action (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    step_no INT NOT NULL,
    approver_id BIGINT NULL COMMENT 'null=系统自动',
    on_behalf_of_user_id BIGINT NULL COMMENT '代审关系',
    decision VARCHAR(16) NOT NULL COMMENT 'APPROVED/REJECTED/SUPPLEMENT/STARTED/TIMEOUT/SKIPPED',
    comment TEXT NULL,
    decided_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_approval_flow_action_instance (instance_id, decided_at),
    KEY idx_approval_flow_action_step (instance_id, step_no),
    CONSTRAINT fk_approval_flow_action_instance FOREIGN KEY (instance_id) REFERENCES approval_flow_instance(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批动作历史';