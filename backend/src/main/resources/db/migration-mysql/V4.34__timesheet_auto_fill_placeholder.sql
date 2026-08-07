-- ============================================================
-- V4.34 工时自动填报 - 占位项目 + system_config 开关
-- 业务规则:
--   1. 自动填报名时,如用户当日无任何项目候选,使用占位项目 PLACEHOLDER
--   2. project_id NOT NULL 约束无法绕开,故必须有一个真实 project_id
--   3. 占位项目 id 由 V4.34 migration 固定创建 (按 code 兜底)
--   4. system_config 加 timesheet.auto_fill.enabled 默认 on
-- ============================================================

-- 1) 兜底占位项目 (idempotent, 按 code 查重)
-- project 表 NOT NULL 列: code, name, type_id, status_id, progress_pct, baseline_version, deleted, created_at, updated_at
-- 没有 enabled 列 (MySQL 实际表)
INSERT INTO project (
    code, name, type_id, status_id, pm_user_id,
    department_id,
    progress_pct, baseline_version, deleted,
    created_at, updated_at
)
SELECT
    'PLACEHOLDER', '空置占位项目',
    (SELECT id FROM project_type   ORDER BY id ASC LIMIT 1),
    (SELECT id FROM project_status WHERE code = 'CLOSED' OR code = 'ACTIVE' OR code = 'ARCHIVED' ORDER BY id ASC LIMIT 1),
    NULL, NULL,
    0, 0, 0,
    NOW(3), NOW(3)
WHERE NOT EXISTS (SELECT 1 FROM project WHERE code = 'PLACEHOLDER');

-- 2) system_config 开关 (默认启用)
-- 列: config_key, config_value, value_type, options, config_group, default_value, description, sort_order, updated_at, updated_by
-- 没有 created_at 列 (MySQL 实际表), updated_at 有 DEFAULT_GENERATED
INSERT INTO system_config (config_key, config_value, value_type, options, config_group, default_value, description, sort_order, updated_by)
VALUES
    ('timesheet.auto_fill.enabled', 'true', 'BOOLEAN', NULL, 'business', 'true', '工时自动填报主开关', 50, 'system')
ON DUPLICATE KEY UPDATE
    config_value  = VALUES(config_value),
    default_value = VALUES(default_value),
    description   = VALUES(description),
    updated_at    = CURRENT_TIMESTAMP(3);
