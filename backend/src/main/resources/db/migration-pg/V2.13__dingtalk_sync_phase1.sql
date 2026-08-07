-- ============================================================
-- V2.13 钉钉通讯录同步 (Phase 1) — schema + config
-- ============================================================

-- 1.1 app_user 加钉钉 userid 列
ALTER TABLE app_user
  ADD COLUMN IF NOT EXISTS dingtalk_user_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_user_dingtalk
  ON app_user(dingtalk_user_id) WHERE dingtalk_user_id IS NOT NULL;

-- 1.2 department 加钉钉 dept_id
ALTER TABLE department
  ADD COLUMN IF NOT EXISTS dingtalk_dept_id BIGINT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_department_dingtalk
  ON department(dingtalk_dept_id) WHERE dingtalk_dept_id IS NOT NULL;

-- 1.3 system_config 钉钉配置 (INTEGRATION group, sort_order 100-105)
INSERT INTO system_config (config_key, config_value, value_type, config_group, default_value, sort_order, description, updated_at) VALUES
  ('integration.dingtalk.enabled',        'false', 'BOOLEAN', 'integration', 'false', 100, '钉钉通讯录同步总开关', now()),
  ('integration.dingtalk.app_key',        '',      'STRING',  'integration', '',      101, '钉钉企业内部应用 AppKey (开放平台 → 应用 → 凭证信息)', now()),
  ('integration.dingtalk.app_secret',     '',      'STRING',  'integration', '',      102, '钉钉企业内部应用 AppSecret (同 AppKey 一起)', now()),
  ('integration.dingtalk.agent_id',        '',      'STRING',  'integration', '',      103, '钉钉企业内部应用 AgentID (开放平台 → 应用 → 基础信息)', now()),
  ('integration.dingtalk.sync_cron',       '0 0 2 * * *', 'STRING', 'integration', '0 0 2 * * *', 104, '同步定时任务 Cron 表达式 (默认每天凌晨 02:00)', now()),
  ('integration.dingtalk.auto_create_user','true',  'BOOLEAN', 'integration', 'true',   105, '同步时自动建账号 (默认角色 = 最低权限角色)', now())
ON CONFLICT (config_key) DO NOTHING;

-- 1.4 audit_log 用 sync_audit_log 表 (单独表, 不污染通用 audit)
CREATE TABLE IF NOT EXISTS dingtalk_sync_log (
  id            BIGSERIAL PRIMARY KEY,
  started_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  finished_at   TIMESTAMP WITH TIME ZONE,
  trigger_type  VARCHAR(16) NOT NULL,            -- CRON / MANUAL
  triggered_by  VARCHAR(64),                      -- 操作人 (CRON 时为 SYSTEM)
  status        VARCHAR(16) NOT NULL,             -- RUNNING / SUCCESS / PARTIAL / FAILED
  total_users   INT NOT NULL DEFAULT 0,           -- 钉钉通讯录用户数
  created_count INT NOT NULL DEFAULT 0,           -- 新建 app_user
  updated_count INT NOT NULL DEFAULT 0,           -- 更新 app_user
  disabled_count INT NOT NULL DEFAULT 0,          -- 离职禁用
  total_depts   INT NOT NULL DEFAULT 0,
  created_dept_count INT NOT NULL DEFAULT 0,
  updated_dept_count INT NOT NULL DEFAULT 0,
  error_message TEXT,
  error_detail  TEXT                              -- stacktrace / API 响应
);
CREATE INDEX IF NOT EXISTS idx_sync_log_started ON dingtalk_sync_log(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_sync_log_status  ON dingtalk_sync_log(status);
