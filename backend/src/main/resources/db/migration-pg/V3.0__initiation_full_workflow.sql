-- ============================================================
-- V3.0 立项全流程增强 (PG 版)
-- 1) 铁三角角色 (AR/SR/FR) 加入角色字典
-- 2) 立项表挂 SOW 文件 / 合同金额 / 客户名 字段
-- 3) initiation_sow_file (SOW 文件元数据)
-- 4) initiation_ai_wbs_draft (AI WBS 草稿暂存,Step 3 用户确认后清空)
-- 5) initiation_resource_plan (人力资源派遣计划)
-- 6) initiation_risk_response (立项阶段风险应对成本)
-- 7) initiation_budget_freeze (预算快照 + 毛利)
-- 8) project_initiation 新增字段: contract_amount (合同金额)
-- 9) initiation_resource_plan 关联 role_id (实际报价)
-- 10) hourly_rate 表已经存在;新增 V3.0 role 同步 insert
-- ============================================================

-- (1) 铁三角角色 (AR/SR/FR)
INSERT INTO role (code, name, description, built_in, sort_order, enabled)
VALUES
    ('AR', '客户经理 (AR)', '客户关系维护 / 合同签署 / 商务红线', TRUE, 5, TRUE),
    ('SR', '售前 (SR)',     '方案设计 / SOW 撰写 / 客户交底',     TRUE, 6, TRUE),
    ('FR', '方案经理 (FR)', '方案交付 / 承接项目 / 商务承诺',     TRUE, 7, TRUE)
ON CONFLICT (code) DO NOTHING;

-- (2) project_initiation 加字段
ALTER TABLE project_initiation
    ADD COLUMN IF NOT EXISTS sow_required        BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS sow_received        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS contract_amount     NUMERIC(14,2),
    ADD COLUMN IF NOT EXISTS contract_currency   VARCHAR(8)             DEFAULT 'CNY',
    ADD COLUMN IF NOT EXISTS client_name         VARCHAR(256),
    ADD COLUMN IF NOT EXISTS client_contact_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS client_contact_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS plan_work_weeks     INT,
    ADD COLUMN IF NOT EXISTS created_by          BIGINT      REFERENCES app_user(id);

CREATE INDEX IF NOT EXISTS idx_init_sow_received ON project_initiation(sow_received);
CREATE INDEX IF NOT EXISTS idx_init_client       ON project_initiation(client_name);

-- (3) SOW 文件元数据
CREATE TABLE IF NOT EXISTS initiation_sow_file (
    id              BIGSERIAL    PRIMARY KEY,
    initiation_id   BIGINT       NOT NULL REFERENCES project_initiation(id) ON DELETE CASCADE,
    file_name       VARCHAR(256) NOT NULL,
    file_path       VARCHAR(512) NOT NULL,           -- 相对路径 uploads/sow/{id}/{filename}
    file_size       BIGINT       NOT NULL,
    content_type    VARCHAR(255),
    uploaded_by     BIGINT       NOT NULL REFERENCES app_user(id),
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_sow_file_init ON initiation_sow_file(initiation_id);
COMMENT ON TABLE initiation_sow_file IS '立项 SOW 文件元数据(实体存文件系统 /uploads/sow/{id}/)';

-- (4) AI WBS 草稿暂存
CREATE TABLE IF NOT EXISTS initiation_ai_wbs_draft (
    id              BIGSERIAL    PRIMARY KEY,
    initiation_id   BIGINT       NOT NULL REFERENCES project_initiation(id) ON DELETE CASCADE,
    draft_json      JSONB        NOT NULL,           -- {milestones, workPackages, risks, modelVersion, generatedAt}
    granularity_weeks INT         NOT NULL DEFAULT 2,
    model_version   VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      BIGINT       REFERENCES app_user(id),
    applied_at      TIMESTAMPTZ,                     -- 用户点击"应用到 WBS"时填
    applied_by      BIGINT       REFERENCES app_user(id),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_ai_draft_init ON initiation_ai_wbs_draft(initiation_id);
CREATE INDEX IF NOT EXISTS idx_ai_draft_unapplied ON initiation_ai_wbs_draft(initiation_id) WHERE applied_at IS NULL;
COMMENT ON TABLE initiation_ai_wbs_draft IS 'AI WBS 转化助手输出(Step 2 暂存,Step 3 用户确认后写 wbs_task)';

-- (5) 人力资源派遣计划 (Step 4)
CREATE TABLE IF NOT EXISTS initiation_resource_plan (
    id              BIGSERIAL    PRIMARY KEY,
    initiation_id   BIGINT       NOT NULL REFERENCES project_initiation(id) ON DELETE CASCADE,
    user_id         BIGINT       REFERENCES app_user(id),    -- 二选一
    role_code       VARCHAR(32),                              -- 或按角色批量派遣
    allocation_pct  INT          NOT NULL DEFAULT 100,        -- 0-100
    plan_hours      NUMERIC(10,2) NOT NULL DEFAULT 0,         -- 估算工时(小时)
    hourly_rate     NUMERIC(10,2) NOT NULL DEFAULT 0,         -- 锁定时的费率
    cost_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,         -- 预计算 = hours × rate × pct/100
    start_date      DATE,
    end_date        DATE,
    note            TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      BIGINT       REFERENCES app_user(id),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_resource_plan_init ON initiation_resource_plan(initiation_id);
CREATE INDEX IF NOT EXISTS idx_resource_plan_user ON initiation_resource_plan(user_id) WHERE deleted = FALSE;
COMMENT ON TABLE initiation_resource_plan IS 'Step 4 人力资源派遣计划 — 锁定费率后冻结成本';

-- (6) 立项阶段风险应对成本 (Step 5)
CREATE TABLE IF NOT EXISTS initiation_risk_response (
    id              BIGSERIAL    PRIMARY KEY,
    initiation_id   BIGINT       NOT NULL REFERENCES project_initiation(id) ON DELETE CASCADE,
    risk_id         BIGINT       REFERENCES risk(id),          -- 可空:先于 risk 表创建
    risk_title      VARCHAR(256) NOT NULL,                     -- 冗余存储,允许独立填写
    risk_level      VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',    -- LOW / MEDIUM / HIGH / CRITICAL
    response_action TEXT         NOT NULL,
    response_cost   NUMERIC(14,2) NOT NULL DEFAULT 0,
    owner_user_id   BIGINT       REFERENCES app_user(id),
    status          VARCHAR(16)  NOT NULL DEFAULT 'PLANNED',   -- PLANNED / IN_PROGRESS / DONE / CANCELLED
    note            TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      BIGINT       REFERENCES app_user(id),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_init_risk_response ON initiation_risk_response(initiation_id);
COMMENT ON TABLE initiation_risk_response IS 'Step 5 立项阶段风险应对(及成本),独立于 risk 模块可工作';

-- (7) 立项预算快照 + 毛利 (Step 6)
CREATE TABLE IF NOT EXISTS initiation_budget_freeze (
    id              BIGSERIAL    PRIMARY KEY,
    initiation_id   BIGINT       NOT NULL REFERENCES project_initiation(id) ON DELETE CASCADE,
    frozen_by       BIGINT       NOT NULL REFERENCES app_user(id),
    frozen_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- 收入
    contract_amount NUMERIC(14,2) NOT NULL DEFAULT 0,

    -- 成本合计
    resource_cost   NUMERIC(14,2) NOT NULL DEFAULT 0,           -- 资源派遣成本
    risk_cost       NUMERIC(14,2) NOT NULL DEFAULT 0,           -- 风险应对成本
    other_cost      NUMERIC(14,2) NOT NULL DEFAULT 0,           -- 其它预算项
    total_cost      NUMERIC(14,2) NOT NULL DEFAULT 0,           -- = resource + risk + other

    -- 毛利
    margin          NUMERIC(14,2) NOT NULL DEFAULT 0,           -- 合同 - 总成本
    margin_pct      NUMERIC(5,2)  NOT NULL DEFAULT 0,           -- 0-100

    -- 详细快照(便于回溯)
    snapshot_json   JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,

    -- 同一立项只允许一个 active 快照(用 unique partial index)
    CONSTRAINT chk_margin_pct CHECK (margin_pct >= 0 AND margin_pct <= 100)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_init_budget_freeze_active
    ON initiation_budget_freeze(initiation_id);
COMMENT ON TABLE initiation_budget_freeze IS 'Step 6 立项预算快照 + 毛利计算 (终态基线)';

-- (8) hourly_rate 为 AR/SR/FR 三角色补默认费率
INSERT INTO hourly_rate (role, rate, effective_date, note)
SELECT 'AR', 400, CURRENT_DATE, 'AR 默认费率(默认值,实际由系统管理维护)'
WHERE NOT EXISTS (SELECT 1 FROM hourly_rate WHERE role = 'AR' AND effective_date = CURRENT_DATE);

INSERT INTO hourly_rate (role, rate, effective_date, note)
SELECT 'SR', 350, CURRENT_DATE, 'SR 默认费率(默认值,实际由系统管理维护)'
WHERE NOT EXISTS (SELECT 1 FROM hourly_rate WHERE role = 'SR' AND effective_date = CURRENT_DATE);

INSERT INTO hourly_rate (role, rate, effective_date, note)
SELECT 'FR', 380, CURRENT_DATE, 'FR 默认费率(默认值,实际由系统管理维护)'
WHERE NOT EXISTS (SELECT 1 FROM hourly_rate WHERE role = 'FR' AND effective_date = CURRENT_DATE);
