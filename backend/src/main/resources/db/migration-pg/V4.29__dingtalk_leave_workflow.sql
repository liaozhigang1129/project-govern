-- ============================================================
-- V4.29 钉钉请休假 / OA 审批同步配置 (Phase 2)
-- ============================================================
-- 新增 system_config 用于请休假同步:
--   - leave_process_codes       模板 processCode (逗号分隔)
--   - leave_window_days         单窗口查询天数 (默认 60)
--   - leave_include_other_types 是否拉请假以外 (出差/外出/加班, 默认 true)
-- ============================================================

INSERT INTO system_config (config_key, config_value, value_type, config_group, default_value, sort_order, description, updated_at) VALUES
  ('integration.dingtalk.leave_process_codes',       'PROC-325BD729-5E99-4E99-9534-A3CB99617938,PROC-87E7F27D-E672-48BB-9B0A-C893CF30B40E,PROC-EF72FC5E-96CF-4B62-BA09-E5E404B85531,PROC-B0EEAB01-2C29-4583-ACDD-28A5451358C2', 'STRING',  'integration', 'PROC-325BD729-5E99-4E99-9534-A3CB99617938', 200, '请假/OA 审批模板 processCode (逗号分隔, 默认含请假/出差/外出/加班)', now()),
  ('integration.dingtalk.leave_window_days',         '60',  'NUMBER',  'integration', '60',  201, '单窗口查询天数 (钉钉 ≤120, 默认 60)', now()),
  ('integration.dingtalk.leave_include_other_types', 'true', 'BOOLEAN', 'integration', 'true', 202, '是否拉取请假以外的 OA 审批 (出差/外出/加班), 默认开启', now())
ON CONFLICT (config_key) DO NOTHING;
