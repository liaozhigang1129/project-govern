-- ============================================================
-- V6.0 通用审批工作流引擎 (PostgreSQL 版)
-- 与 MySQL 版 V6.0__approval_engine.sql 同构 (数据类型调整)
-- ============================================================

CREATE TABLE approval_flow_def (
    id BIGSERIAL PRIMARY KEY,
    kind VARCHAR(32) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (kind, code, version),
    COMMENT ON TABLE approval_flow_def IS '审批流程定义'
);

CREATE TABLE approval_flow_step (
    id BIGSERIAL PRIMARY KEY,
    flow_def_id BIGINT NOT NULL REFERENCES approval_flow_def(id) ON DELETE CASCADE,
    step_no INT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    auto_approve_when BOOLEAN NOT NULL DEFAULT FALSE,
    skip_when VARCHAR(256),
    timeout_hours INT,
    UNIQUE (flow_def_id, step_no),
    COMMENT ON TABLE approval_flow_step IS '审批流程步骤'
);
CREATE INDEX idx_approval_flow_step_role ON approval_flow_step(role_code);

CREATE TABLE approval_flow_instance (
    id BIGSERIAL PRIMARY KEY,
    flow_def_id BIGINT NOT NULL REFERENCES approval_flow_def(id),
    kind VARCHAR(32) NOT NULL,
    biz_id BIGINT NOT NULL,
    biz_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    current_step_no INT NOT NULL DEFAULT 0,
    applicant_id BIGINT NOT NULL,
    department_id BIGINT,
    biz_payload TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP(3),
    UNIQUE (kind, biz_id),
    COMMENT ON TABLE approval_flow_instance IS '审批流程实例'
);
CREATE INDEX idx_approval_flow_instance_status ON approval_flow_instance(status, created_at);
CREATE INDEX idx_approval_flow_instance_applicant ON approval_flow_instance(applicant_id, status);

CREATE TABLE approval_flow_action (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES approval_flow_instance(id) ON DELETE CASCADE,
    step_no INT NOT NULL,
    approver_id BIGINT,
    on_behalf_of_user_id BIGINT,
    decision VARCHAR(16) NOT NULL,
    comment TEXT,
    decided_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    COMMENT ON TABLE approval_flow_action IS '审批动作历史'
);
CREATE INDEX idx_approval_flow_action_instance ON approval_flow_action(instance_id, decided_at);
CREATE INDEX idx_approval_flow_action_step ON approval_flow_action(instance_id, step_no);