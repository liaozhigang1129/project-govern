-- ============================================================
-- V1.3 立项 + 审批 + 里程碑
-- ============================================================

-- 立项申请状态字典
CREATE TABLE initiation_status (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) UNIQUE NOT NULL,    -- DRAFT/PENDING/DEPT_APPROVED/PMO_APPROVED/EXEC_APPROVED/REJECTED/SUPPLEMENT
    name            VARCHAR(64) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    is_terminal     BOOLEAN NOT NULL DEFAULT FALSE
);
COMMENT ON TABLE initiation_status IS '立项状态字典';

-- 立项申请表
CREATE TABLE project_initiation (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) UNIQUE NOT NULL,    -- IR-2025-001
    project_id      BIGINT REFERENCES project(id) ON DELETE SET NULL, -- 批准后回写
    title           VARCHAR(256) NOT NULL,         -- 立项标题(冗余项目名,便于审批列表展示)
    applicant_id    BIGINT NOT NULL REFERENCES app_user(id),
    department_id   BIGINT REFERENCES department(id),
    background      TEXT NOT NULL,
    goals           TEXT NOT NULL,
    scope           TEXT NOT NULL,
    plan_workdays   INT,
    budget_estimate NUMERIC(14,2),
    planned_start   DATE,
    planned_end     DATE,
    initial_risks   TEXT,
    status_id       BIGINT NOT NULL REFERENCES initiation_status(id),
    current_step    VARCHAR(32),                   -- 当前审批步骤: DEPT/PMO/EXEC
    submitted_at    TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,                   -- 最终审批完成时间
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_initiation_status ON project_initiation(status_id);
CREATE INDEX idx_initiation_applicant ON project_initiation(applicant_id);
CREATE INDEX idx_initiation_project ON project_initiation(project_id);
CREATE INDEX idx_initiation_deleted ON project_initiation(deleted);
COMMENT ON TABLE project_initiation IS '立项申请表';

-- 审批步骤字典(固定3级)
CREATE TABLE approval_step (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) UNIQUE NOT NULL,    -- DEPT_LEAD / PMO_ADMIN / EXEC
    name            VARCHAR(64) NOT NULL,
    sequence        INT NOT NULL,                  -- 1/2/3
    description     VARCHAR(256)
);
COMMENT ON TABLE approval_step IS '审批步骤字典';

-- 审批记录表
CREATE TABLE approval_record (
    id              BIGSERIAL PRIMARY KEY,
    initiation_id   BIGINT NOT NULL REFERENCES project_initiation(id) ON DELETE CASCADE,
    step_id         BIGINT NOT NULL REFERENCES approval_step(id),
    approver_id     BIGINT NOT NULL REFERENCES app_user(id),
    decision        VARCHAR(16) NOT NULL,          -- APPROVED/REJECTED/SUPPLEMENT
    comment         TEXT,
    decided_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_approval_initiation ON approval_record(initiation_id);
CREATE INDEX idx_approval_approver ON approval_record(approver_id);
COMMENT ON TABLE approval_record IS '审批记录表';

-- 里程碑状态字典
CREATE TABLE milestone_status (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) UNIQUE NOT NULL,    -- PENDING/IN_PROGRESS/COMPLETED/DELAYED
    name            VARCHAR(64) NOT NULL,
    is_terminal     BOOLEAN NOT NULL DEFAULT FALSE
);
COMMENT ON TABLE milestone_status IS '里程碑状态字典';

-- 里程碑表
CREATE TABLE milestone (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name            VARCHAR(128) NOT NULL,
    sequence        INT NOT NULL,                  -- 序号 1/2/3...
    plan_date       DATE NOT NULL,
    actual_date     DATE,
    status_id       BIGINT NOT NULL REFERENCES milestone_status(id),
    weight          INT NOT NULL DEFAULT 1 CHECK (weight BETWEEN 1 AND 10), -- 权重,用于加权进度
    owner_user_id   BIGINT REFERENCES app_user(id),
    deliverable     TEXT,                          -- 交付物说明
    remark          TEXT,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(project_id, sequence)
);
CREATE INDEX idx_milestone_project ON milestone(project_id);
CREATE INDEX idx_milestone_status ON milestone(status_id);
CREATE INDEX idx_milestone_plan_date ON milestone(plan_date);
COMMENT ON TABLE milestone IS '里程碑表';

-- 操作日志(简易审计,Phase 2 可替换为统一审计服务)
CREATE TABLE operation_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES app_user(id),
    resource_type   VARCHAR(32) NOT NULL,          -- PROJECT/INITIATION/MILESTONE/USER
    resource_id     BIGINT,
    action          VARCHAR(32) NOT NULL,          -- CREATE/UPDATE/DELETE/APPROVE/REJECT
    payload         JSONB,                          -- 变更前后快照
    ip_address      VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_oplog_user ON operation_log(user_id);
CREATE INDEX idx_oplog_resource ON operation_log(resource_type, resource_id);
CREATE INDEX idx_oplog_created ON operation_log(created_at DESC);
COMMENT ON TABLE operation_log IS '操作日志';

-- 添加 triggers
DROP TRIGGER IF EXISTS trg_department_updated_at ON department;
CREATE TRIGGER trg_department_updated_at BEFORE UPDATE ON department
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_user_updated_at ON app_user;
CREATE TRIGGER trg_user_updated_at BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_project_updated_at ON project;
CREATE TRIGGER trg_project_updated_at BEFORE UPDATE ON project
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_initiation_updated_at ON project_initiation;
CREATE TRIGGER trg_initiation_updated_at BEFORE UPDATE ON project_initiation
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_milestone_updated_at ON milestone;
CREATE TRIGGER trg_milestone_updated_at BEFORE UPDATE ON milestone
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();
