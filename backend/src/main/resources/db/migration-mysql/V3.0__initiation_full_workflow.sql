-- ============================================================
-- V3.0 立项全流程增强 (MySQL 版)
-- 1) 铁三角角色 (AR/SR/FR) 加入角色字典
-- 2) 立项表挂 SOW 文件 / 合同金额 / 客户名 字段
-- 3) initiation_sow_file (SOW 文件元数据)
-- 4) initiation_ai_wbs_draft (AI WBS 草稿暂存,Step 3 用户确认后清空)
-- 5) initiation_resource_plan (人力资源派遣计划)
-- 6) initiation_risk_response (立项阶段风险应对成本)
-- 7) initiation_budget_freeze (预算快照 + 毛利)
-- 8) hourly_rate 为 AR/SR/FR 三角色补默认费率
-- ============================================================

-- (1) 铁三角角色
INSERT IGNORE INTO role (code, name, description, built_in, sort_order, enabled) VALUES
    ('AR', '客户经理 (AR)', '客户关系维护 / 合同签署 / 商务红线', 1, 5, 1),
    ('SR', '售前 (SR)',     '方案设计 / SOW 撰写 / 客户交底',     1, 6, 1),
    ('FR', '方案经理 (FR)', '方案交付 / 承接项目 / 商务承诺',     1, 7, 1);

-- (2) project_initiation 加字段
-- MySQL 8.0.46 不支持 ADD COLUMN IF NOT EXISTS, 改用存储过程判存在
SET @db = DATABASE();

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project_initiation' AND COLUMN_NAME='sow_required')=0,
  'ALTER TABLE project_initiation ADD COLUMN sow_required TINYINT(1) NOT NULL DEFAULT 1',
  'SELECT 1')); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project_initiation' AND COLUMN_NAME='sow_received')=0,
  'ALTER TABLE project_initiation ADD COLUMN sow_received TINYINT(1) NOT NULL DEFAULT 0',
  'SELECT 1')); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project_initiation' AND COLUMN_NAME='contract_amount')=0,
  'ALTER TABLE project_initiation ADD COLUMN contract_amount DECIMAL(14,2)',
  'SELECT 1')); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project_initiation' AND COLUMN_NAME='contract_currency')=0,
  "ALTER TABLE project_initiation ADD COLUMN contract_currency VARCHAR(8) DEFAULT 'CNY'",
  'SELECT 1')); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project_initiation' AND COLUMN_NAME='client_name')=0,
  'ALTER TABLE project_initiation ADD COLUMN client_name VARCHAR(256)',
  'SELECT 1')); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project_initiation' AND COLUMN_NAME='client_contact_name')=0,
  'ALTER TABLE project_initiation ADD COLUMN client_contact_name VARCHAR(128)',
  'SELECT 1')); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project_initiation' AND COLUMN_NAME='client_contact_phone')=0,
  'ALTER TABLE project_initiation ADD COLUMN client_contact_phone VARCHAR(32)',
  'SELECT 1')); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project_initiation' AND COLUMN_NAME='plan_work_weeks')=0,
  'ALTER TABLE project_initiation ADD COLUMN plan_work_weeks INT',
  'SELECT 1')); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='project_initiation' AND COLUMN_NAME='created_by')=0,
  'ALTER TABLE project_initiation ADD COLUMN created_by BIGINT',
  'SELECT 1')); PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

CREATE INDEX idx_init_sow_received ON project_initiation(sow_received);
CREATE INDEX idx_init_client       ON project_initiation(client_name);

-- (3) SOW 文件元数据
CREATE TABLE IF NOT EXISTS initiation_sow_file (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    initiation_id   BIGINT       NOT NULL,
    file_name       VARCHAR(256) NOT NULL,
    file_path       VARCHAR(512) NOT NULL,
    file_size       BIGINT       NOT NULL,
    -- V4.22 修复: 浏览器 multipart/form-data 的 Content-Type 形如
    --   "multipart/form-data; boundary=----WebKitFormBoundary..." (70~200 字节)
    -- 旧 VARCHAR(64) 会触发 MySQL "Data too long"。业务代码 (InitiationSowFileService#sanitizeContentType)
    -- 只保留 type/subtype 主体部分, 这里把列宽扩到 VARCHAR(255) 作为双层保险。
    content_type    VARCHAR(255),
    uploaded_by     BIGINT       NOT NULL,
    uploaded_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    CONSTRAINT fk_sow_init FOREIGN KEY (initiation_id) REFERENCES project_initiation(id) ON DELETE CASCADE,
    CONSTRAINT fk_sow_user FOREIGN KEY (uploaded_by)   REFERENCES app_user(id)
);
CREATE INDEX idx_sow_file_init ON initiation_sow_file(initiation_id);

-- (4) AI WBS 草稿暂存
CREATE TABLE IF NOT EXISTS initiation_ai_wbs_draft (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    initiation_id     BIGINT       NOT NULL,
    draft_json        JSON         NOT NULL,
    granularity_weeks INT          NOT NULL DEFAULT 2,
    model_version     VARCHAR(64),
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT,
    applied_at        DATETIME,
    applied_by        BIGINT,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           TINYINT(1)   NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_draft_init FOREIGN KEY (initiation_id) REFERENCES project_initiation(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_draft_init ON initiation_ai_wbs_draft(initiation_id);

-- (5) 人力资源派遣计划
CREATE TABLE IF NOT EXISTS initiation_resource_plan (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    initiation_id   BIGINT        NOT NULL,
    user_id         BIGINT,
    role_code       VARCHAR(32),
    allocation_pct  INT           NOT NULL DEFAULT 100,
    plan_hours      DECIMAL(10,2) NOT NULL DEFAULT 0,
    hourly_rate     DECIMAL(10,2) NOT NULL DEFAULT 0,
    cost_amount     DECIMAL(14,2) NOT NULL DEFAULT 0,
    start_date      DATE,
    end_date        DATE,
    note            TEXT,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)    NOT NULL DEFAULT 0,
    CONSTRAINT fk_rp_init FOREIGN KEY (initiation_id) REFERENCES project_initiation(id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_user FOREIGN KEY (user_id)       REFERENCES app_user(id)
);
CREATE INDEX idx_resource_plan_init ON initiation_resource_plan(initiation_id);

-- (6) 立项阶段风险应对成本
CREATE TABLE IF NOT EXISTS initiation_risk_response (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    initiation_id   BIGINT       NOT NULL,
    risk_id         BIGINT,
    risk_title      VARCHAR(256) NOT NULL,
    risk_level      VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    response_action TEXT         NOT NULL,
    response_cost   DECIMAL(14,2) NOT NULL DEFAULT 0,
    owner_user_id   BIGINT,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PLANNED',
    note            TEXT,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    CONSTRAINT fk_irr_init  FOREIGN KEY (initiation_id) REFERENCES project_initiation(id) ON DELETE CASCADE,
    CONSTRAINT fk_irr_risk  FOREIGN KEY (risk_id)       REFERENCES risk(id),
    CONSTRAINT fk_irr_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_init_risk_response ON initiation_risk_response(initiation_id);

-- (7) 立项预算快照 + 毛利
CREATE TABLE IF NOT EXISTS initiation_budget_freeze (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    initiation_id   BIGINT        NOT NULL,
    frozen_by       BIGINT        NOT NULL,
    frozen_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    contract_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    resource_cost   DECIMAL(14,2) NOT NULL DEFAULT 0,
    risk_cost       DECIMAL(14,2) NOT NULL DEFAULT 0,
    other_cost      DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_cost      DECIMAL(14,2) NOT NULL DEFAULT 0,
    margin          DECIMAL(14,2) NOT NULL DEFAULT 0,
    margin_pct      DECIMAL(5,2)  NOT NULL DEFAULT 0,
    snapshot_json   JSON          NOT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)    NOT NULL DEFAULT 0,
    CONSTRAINT fk_ibf_init  FOREIGN KEY (initiation_id) REFERENCES project_initiation(id) ON DELETE CASCADE,
    CONSTRAINT fk_ibf_frozen FOREIGN KEY (frozen_by)   REFERENCES app_user(id),
    CONSTRAINT chk_margin_pct CHECK (margin_pct >= 0 AND margin_pct <= 100)
);
CREATE UNIQUE INDEX uk_init_budget_freeze_active ON initiation_budget_freeze(initiation_id);

-- (8) hourly_rate 为 AR/SR/FR 三角色补默认费率
INSERT INTO hourly_rate (role, rate, effective_date, note)
SELECT 'AR', 400, CURRENT_DATE, 'AR 默认费率(默认值,实际由系统管理维护)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM hourly_rate WHERE role = 'AR' AND effective_date = CURRENT_DATE);

INSERT INTO hourly_rate (role, rate, effective_date, note)
SELECT 'SR', 350, CURRENT_DATE, 'SR 默认费率(默认值,实际由系统管理维护)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM hourly_rate WHERE role = 'SR' AND effective_date = CURRENT_DATE);

INSERT INTO hourly_rate (role, rate, effective_date, note)
SELECT 'FR', 380, CURRENT_DATE, 'FR 默认费率(默认值,实际由系统管理维护)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM hourly_rate WHERE role = 'FR' AND effective_date = CURRENT_DATE);
