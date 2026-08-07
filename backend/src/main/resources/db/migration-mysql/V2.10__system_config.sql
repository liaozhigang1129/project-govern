-- ============================================================
-- V2.10 系统参数配置 (MySQL 版)
-- 与 PG 版同构,字段/类型/分组严格对齐
-- ============================================================

CREATE TABLE IF NOT EXISTS system_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    config_key      VARCHAR(128)    NOT NULL UNIQUE,
    config_value    TEXT,
    value_type      VARCHAR(16)     NOT NULL,
    options         VARCHAR(256),
    config_group    VARCHAR(32)     NOT NULL,
    default_value   TEXT,
    description     VARCHAR(256),
    sort_order      INT             NOT NULL DEFAULT 100,
    updated_at      TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by      VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_syscfg_group ON system_config(config_group);

-- 安全组
INSERT INTO system_config (config_key, config_value, value_type, options, config_group, default_value, description, sort_order) VALUES
    ('security.jwt.ttl-minutes',          '120',         'NUMBER', NULL,           'security',  '120',         'JWT 访问令牌有效期(分钟)',     10),
    ('security.jwt.refresh-ttl-days',     '14',          'NUMBER', NULL,           'security',  '14',          'JWT 刷新令牌有效期(天)',       20),
    ('security.login.max-fail-count',     '5',           'NUMBER', NULL,           'security',  '5',           '连续登录失败锁定阈值',         30),
    ('security.login.lock-minutes',       '30',          'NUMBER', NULL,           'security',  '30',          '账号锁定时长(分钟)',           40),
    ('security.password.min-length',      '8',           'NUMBER', NULL,           'security',  '8',           '密码最小长度',                 50)
ON DUPLICATE KEY UPDATE config_key = config_key;

-- 业务组
INSERT INTO system_config (config_key, config_value, value_type, options, config_group, default_value, description, sort_order) VALUES
    ('business.timesheet.week-starts-on', 'MONDAY',      'ENUM',   'MONDAY,SUNDAY', 'business', 'MONDAY',     '工时周报起始日',               10),
    ('business.timesheet.reminder-day',   'WEDNESDAY',   'ENUM',   'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY', 'business', 'WEDNESDAY', '工时催办日',         20),
    ('business.timesheet.auto-lock-weeks','13',          'NUMBER', NULL,           'business',  '13',          '历史周报自动归档周数',         30),
    ('business.project.carryover-days',   '30',          'NUMBER', NULL,           'business',  '30',          '项目计划自动顺延天数',         40)
ON DUPLICATE KEY UPDATE config_key = config_key;

-- 集成组
INSERT INTO system_config (config_key, config_value, value_type, options, config_group, default_value, description, sort_order) VALUES
    ('integration.dingtalk.enabled',      'false',       'BOOLEAN',NULL,           'integration','false',      '是否启用钉钉集成',             10),
    ('integration.dingtalk.corp-id',      '',            'STRING', NULL,           'integration','',            '钉钉企业 corpId',              20),
    ('integration.mail.enabled',          'true',        'BOOLEAN',NULL,           'integration','true',       '是否启用邮件通知',             30)
ON DUPLICATE KEY UPDATE config_key = config_key;