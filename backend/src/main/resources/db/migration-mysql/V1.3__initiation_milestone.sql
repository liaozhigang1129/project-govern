-- ============================================================
-- V1.3 立项 + 审批 + 里程碑(MySQL 8 方言)
-- ============================================================

-- 立项申请状态字典
CREATE TABLE initiation_status (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE,    -- DRAFT/PENDING/DEPT_APPROVED/PMO_APPROVED/EXEC_APPROVED/REJECTED/SUPPLEMENT
    name            VARCHAR(64)  NOT NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    is_terminal     BOOLEAN      NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='立项状态字典';

-- 立项申请表
CREATE TABLE project_initiation (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE,    -- IR-2025-001
    project_id      BIGINT       NULL, -- 批准后回写
    title           VARCHAR(256) NOT NULL,         -- 立项标题(冗余项目名,便于审批列表展示)
    applicant_id    BIGINT       NOT NULL,
    department_id   BIGINT       NULL,
    background      TEXT         NOT NULL,
    goals           TEXT         NOT NULL,
    scope           TEXT         NOT NULL,
    plan_workdays   INT          NULL,
    budget_estimate DECIMAL(14,2) NULL,
    planned_start   DATE         NULL,
    planned_end     DATE         NULL,
    initial_risks   TEXT         NULL,
    status_id       BIGINT       NOT NULL,
    current_step    VARCHAR(32)  NULL,                   -- 当前审批步骤: DEPT/PMO/EXEC
    submitted_at    DATETIME(3)  NULL,
    closed_at       DATETIME(3)  NULL,                   -- 最终审批完成时间
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_initiation_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE SET NULL,
    CONSTRAINT fk_initiation_applicant FOREIGN KEY (applicant_id) REFERENCES app_user(id),
    CONSTRAINT fk_initiation_dept FOREIGN KEY (department_id) REFERENCES department(id),
    CONSTRAINT fk_initiation_status FOREIGN KEY (status_id) REFERENCES initiation_status(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='立项申请表';
CREATE INDEX idx_initiation_status ON project_initiation(status_id);
CREATE INDEX idx_initiation_applicant ON project_initiation(applicant_id);
CREATE INDEX idx_initiation_project ON project_initiation(project_id);
CREATE INDEX idx_initiation_deleted ON project_initiation(deleted);

-- 审批步骤字典(固定3级)
CREATE TABLE approval_step (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE,    -- DEPT_LEAD / PMO_ADMIN / EXEC
    name            VARCHAR(64)  NOT NULL,
    sequence        INT          NOT NULL,                  -- 1/2/3
    description     VARCHAR(256) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批步骤字典';

-- 审批记录表
CREATE TABLE approval_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    initiation_id   BIGINT       NOT NULL,
    step_id         BIGINT       NOT NULL,
    approver_id     BIGINT       NOT NULL,
    decision        VARCHAR(16)  NOT NULL,          -- APPROVED/REJECTED/SUPPLEMENT
    comment         TEXT         NULL,
    decided_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_approval_initiation FOREIGN KEY (initiation_id) REFERENCES project_initiation(id) ON DELETE CASCADE,
    CONSTRAINT fk_approval_step FOREIGN KEY (step_id) REFERENCES approval_step(id),
    CONSTRAINT fk_approval_approver FOREIGN KEY (approver_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批记录表';
CREATE INDEX idx_approval_initiation ON approval_record(initiation_id);
CREATE INDEX idx_approval_approver ON approval_record(approver_id);

-- 里程碑状态字典
CREATE TABLE milestone_status (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32)  NOT NULL UNIQUE,    -- PENDING/IN_PROGRESS/COMPLETED/DELAYED
    name            VARCHAR(64)  NOT NULL,
    is_terminal     BOOLEAN      NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='里程碑状态字典';

-- 里程碑表
CREATE TABLE milestone (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    name            VARCHAR(128) NOT NULL,
    sequence        INT          NOT NULL,                  -- 序号 1/2/3...
    plan_date       DATE         NOT NULL,
    actual_date     DATE         NULL,
    status_id       BIGINT       NOT NULL,
    weight          INT          NOT NULL DEFAULT 1, -- 权重,用于加权进度
    owner_user_id   BIGINT       NULL,
    deliverable     TEXT         NULL,                          -- 交付物说明
    remark          TEXT         NULL,
    completed_at    DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE KEY uk_milestone_seq (project_id, sequence),
    CONSTRAINT fk_milestone_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_milestone_status FOREIGN KEY (status_id) REFERENCES milestone_status(id),
    CONSTRAINT fk_milestone_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
    CONSTRAINT chk_milestone_weight CHECK (weight BETWEEN 1 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='里程碑表';
CREATE INDEX idx_milestone_project ON milestone(project_id);
CREATE INDEX idx_milestone_status ON milestone(status_id);
CREATE INDEX idx_milestone_plan_date ON milestone(plan_date);

-- 操作日志(简易审计,Phase 2 可替换为统一审计服务)
CREATE TABLE operation_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NULL,
    resource_type   VARCHAR(32)  NOT NULL,          -- PROJECT/INITIATION/MILESTONE/USER
    resource_id     BIGINT       NULL,
    action          VARCHAR(32)  NOT NULL,          -- CREATE/UPDATE/DELETE/APPROVE/REJECT
    payload         JSON         NULL,                          -- 变更前后快照
    ip_address      VARCHAR(64)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_oplog_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志';
CREATE INDEX idx_oplog_user ON operation_log(user_id);
CREATE INDEX idx_oplog_resource ON operation_log(resource_type, resource_id);
CREATE INDEX idx_oplog_created ON operation_log(created_at);

-- updated_at 触发器(MySQL 8 BEFORE UPDATE)
-- 已在各表 ON UPDATE CURRENT_TIMESTAMP(3) 内置,不需独立触发器
-- 这里只补 milestone / initiation(如需在 service 层显式赋值场景下也自动维护,
--  ON UPDATE 子句已足够)
