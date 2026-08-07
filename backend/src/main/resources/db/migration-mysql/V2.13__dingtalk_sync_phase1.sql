-- ============================================================
-- V2.13 钉钉通讯录同步 (Phase 1) — schema + config (MySQL 版)
-- 与 PG 版同构, 差异:
--   - 去掉 IF NOT EXISTS (ADD COLUMN / CREATE UNIQUE INDEX)
--   - 用存储过程判存在
--   - TIMESTAMPTZ → DATETIME(6)
--   - BOOLEAN → TINYINT(1)
--   - ON CONFLICT → INSERT IGNORE
-- ============================================================
SET @db = DATABASE();

-- 1.1 app_user 加 dingtalk_user_id 列
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='app_user' AND COLUMN_NAME='dingtalk_user_id')=0,
  'ALTER TABLE app_user ADD COLUMN dingtalk_user_id VARCHAR(64) NULL', 'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- MySQL 8.0 不支持部分索引 (WHERE), 用虚拟生成列或简化处理:
-- 这里去 WHERE 子句, 允许 NULL 多行 (项目代码仍会查 dingtalk_user_id 唯一)
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='app_user' AND INDEX_NAME='uq_app_user_dingtalk');
SET @stmt = IF(@idx=0, 'CREATE UNIQUE INDEX uq_app_user_dingtalk ON app_user(dingtalk_user_id)', 'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.2 department 加 dingtalk_dept_id
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='department' AND COLUMN_NAME='dingtalk_dept_id')=0,
  'ALTER TABLE department ADD COLUMN dingtalk_dept_id BIGINT NULL', 'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='department' AND INDEX_NAME='uq_department_dingtalk');
SET @stmt = IF(@idx=0, 'CREATE UNIQUE INDEX uq_department_dingtalk ON department(dingtalk_dept_id)', 'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.3 system_config 钉钉配置
INSERT IGNORE INTO system_config (config_key, config_value, value_type, config_group, default_value, sort_order, description, updated_at) VALUES
  ('integration.dingtalk.enabled',        'false', 'BOOLEAN', 'integration', 'false', 100, '钉钉通讯录同步总开关', NOW(6)),
  ('integration.dingtalk.app_key',        '',      'STRING',  'integration', '',      101, '钉钉企业内部应用 AppKey (开放平台 → 应用 → 凭证信息)', NOW(6)),
  ('integration.dingtalk.app_secret',     '',      'STRING',  'integration', '',      102, '钉钉企业内部应用 AppSecret (同 AppKey 一起)', NOW(6)),
  ('integration.dingtalk.agent_id',        '',      'STRING',  'integration', '',      103, '钉钉企业内部应用 AgentID (开放平台 → 应用 → 基础信息)', NOW(6)),
  ('integration.dingtalk.sync_cron',       '0 0 2 * * *', 'STRING', 'integration', '0 0 2 * * *', 104, '同步定时任务 Cron 表达式 (默认每天凌晨 02:00)', NOW(6)),
  ('integration.dingtalk.auto_create_user','true',  'BOOLEAN', 'integration', 'true',   105, '同步时自动建账号 (默认角色 = 最低权限角色)', NOW(6));

-- 1.4 dingtalk_sync_log
CREATE TABLE IF NOT EXISTS dingtalk_sync_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  started_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  finished_at   DATETIME(6)  NULL,
  trigger_type  VARCHAR(16)  NOT NULL,
  triggered_by  VARCHAR(64),
  status        VARCHAR(16)  NOT NULL,
  total_users   INT          NOT NULL DEFAULT 0,
  created_count INT          NOT NULL DEFAULT 0,
  updated_count INT          NOT NULL DEFAULT 0,
  disabled_count INT         NOT NULL DEFAULT 0,
  total_depts   INT          NOT NULL DEFAULT 0,
  created_dept_count INT     NOT NULL DEFAULT 0,
  updated_dept_count INT     NOT NULL DEFAULT 0,
  error_message TEXT,
  error_detail  TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钉钉通讯录同步审计日志';

CREATE INDEX idx_sync_log_started ON dingtalk_sync_log(started_at);
CREATE INDEX idx_sync_log_status  ON dingtalk_sync_log(status);
