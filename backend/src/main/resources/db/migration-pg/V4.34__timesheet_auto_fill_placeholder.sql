-- ============================================================
-- V4.34 工时自动填报 - 占位项目 + system_config 开关 (PG 版)
-- 与 mysql 版等价,语法调整:
--   - PG 用 ON CONFLICT ... DO UPDATE / DO NOTHING
--   - 时间戳用 NOW()
--   - 字符串拼接 (||)
-- ============================================================

-- 1) 兜底占位项目 (idempotent, 按 code 查重)
INSERT INTO project (
    code, name, type_id, status_id, pm_user_id,
    department_id,
    deleted, enabled, created_at, updated_at
)
SELECT
    'PLACEHOLDER', '空置占位项目',
    (SELECT id FROM project_type   ORDER BY id ASC LIMIT 1),
    (SELECT id FROM project_status ORDER BY id ASC LIMIT 1),
    NULL, NULL,
    false, false, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM project WHERE code = 'PLACEHOLDER');

-- 2) system_config 开关
-- 列对齐 V2.10: config_key, config_value, value_type, options, config_group, default_value, description, sort_order
INSERT INTO system_config (config_key, config_value, value_type, options, config_group, default_value, description, sort_order)
VALUES
    ('timesheet.auto_fill.enabled', 'true', 'BOOLEAN', NULL, 'business', 'true', '工时自动填报主开关', 50)
ON CONFLICT (config_key) DO UPDATE
SET description = EXCLUDED.description,
    updated_at  = NOW();
