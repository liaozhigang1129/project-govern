-- ============================================================
-- V7.1 reporting 模块种子数据 — MySQL 版
-- WP-M7-02 v5 数据模型增量 (8 dashboard + 9 template + 5 dataset + 8 system_config)
-- 幂等: INSERT IGNORE (MySQL)
-- 对应 spec: docs/specs/reporting.md / reporting-api.md
-- 对应 ADR-005 D4(导出)/ D5(数据集)/ D6(订阅)/ D7(安全)
-- ============================================================

-- ============================================================
-- §A 5 个核心数据集 (DS_*)
-- ============================================================
INSERT IGNORE INTO dataset (code, name, domain, source_table, refresh_policy, status, description)
VALUES
    ('DS_PROJECT_PROFIT', '项目利润分析', 'FINANCE', 'project', 'DAILY', 'PUBLISHED',
     '项目维度的收入/成本/利润指标(v5.0 财务模块)'),
    ('DS_RESOURCE_UTIL', '资源利用率', 'RESOURCE', 'wbs_task', 'DAILY', 'PUBLISHED',
     '成员维度的工时利用率/饱和度/成本'),
    ('DS_RISK_MATRIX', '风险矩阵', 'RISK', 'risk', 'HOURLY', 'PUBLISHED',
     '风险热力图数据(概率×影响×热分数)'),
    ('DS_MILESTONE_EVM', '里程碑 EVM', 'SCHEDULE', 'milestone', 'DAILY', 'PUBLISHED',
     '挣值管理 PV/EV/AC/SPI/CPI 指标'),
    ('DS_BUDGET_VS_ACTUAL', '预算 vs 实际', 'FINANCE', 'project', 'DAILY', 'PUBLISHED',
     '预算执行偏差(BCWS/BCWP/ACWP/SV/CV)');

-- DS_PROJECT_PROFIT 字段 (8 个)
INSERT IGNORE INTO dataset_field (dataset_id, field_name, display_name, field_type, data_type, agg_func, dim_role, sort_order)
SELECT id, 'project_id', '项目 ID', 'DIMENSION', 'BIGINT', NULL, 'ROW', 1 FROM dataset WHERE code = 'DS_PROJECT_PROFIT'
UNION ALL SELECT id, 'project_name', '项目名称', 'DIMENSION', 'VARCHAR', NULL, 'ROW', 2 FROM dataset WHERE code = 'DS_PROJECT_PROFIT'
UNION ALL SELECT id, 'period', '期间', 'DIMENSION', 'DATE', NULL, 'COLUMN', 3 FROM dataset WHERE code = 'DS_PROJECT_PROFIT'
UNION ALL SELECT id, 'revenue', '收入', 'MEASURE', 'NUMERIC', 'SUM', NULL, 4 FROM dataset WHERE code = 'DS_PROJECT_PROFIT'
UNION ALL SELECT id, 'cost', '成本', 'MEASURE', 'NUMERIC', 'SUM', NULL, 5 FROM dataset WHERE code = 'DS_PROJECT_PROFIT'
UNION ALL SELECT id, 'profit', '利润', 'MEASURE', 'NUMERIC', 'SUM', NULL, 6 FROM dataset WHERE code = 'DS_PROJECT_PROFIT'
UNION ALL SELECT id, 'profit_margin', '利润率', 'MEASURE', 'NUMERIC', 'AVG', NULL, 7 FROM dataset WHERE code = 'DS_PROJECT_PROFIT'
UNION ALL SELECT id, 'health_score', '健康度', 'MEASURE', 'INT', 'AVG', NULL, 8 FROM dataset WHERE code = 'DS_PROJECT_PROFIT';

-- DS_RESOURCE_UTIL 字段 (8 个)
INSERT IGNORE INTO dataset_field (dataset_id, field_name, display_name, field_type, data_type, agg_func, dim_role, sort_order)
SELECT id, 'user_id', '成员 ID', 'DIMENSION', 'BIGINT', NULL, 'ROW', 1 FROM dataset WHERE code = 'DS_RESOURCE_UTIL'
UNION ALL SELECT id, 'user_name', '成员姓名', 'DIMENSION', 'VARCHAR', NULL, 'ROW', 2 FROM dataset WHERE code = 'DS_RESOURCE_UTIL'
UNION ALL SELECT id, 'period', '期间', 'DIMENSION', 'DATE', NULL, 'COLUMN', 3 FROM dataset WHERE code = 'DS_RESOURCE_UTIL'
UNION ALL SELECT id, 'allocated_hours', '分配工时', 'MEASURE', 'NUMERIC', 'SUM', NULL, 4 FROM dataset WHERE code = 'DS_RESOURCE_UTIL'
UNION ALL SELECT id, 'actual_hours', '实际工时', 'MEASURE', 'NUMERIC', 'SUM', NULL, 5 FROM dataset WHERE code = 'DS_RESOURCE_UTIL'
UNION ALL SELECT id, 'utilization', '利用率', 'MEASURE', 'NUMERIC', 'AVG', NULL, 6 FROM dataset WHERE code = 'DS_RESOURCE_UTIL'
UNION ALL SELECT id, 'saturation', '饱和度', 'MEASURE', 'NUMERIC', 'AVG', NULL, 7 FROM dataset WHERE code = 'DS_RESOURCE_UTIL'
UNION ALL SELECT id, 'cost', '成本', 'MEASURE', 'NUMERIC', 'SUM', NULL, 8 FROM dataset WHERE code = 'DS_RESOURCE_UTIL';

-- DS_RISK_MATRIX 字段 (8 个)
INSERT IGNORE INTO dataset_field (dataset_id, field_name, display_name, field_type, data_type, agg_func, dim_role, sort_order)
SELECT id, 'risk_id', '风险 ID', 'DIMENSION', 'BIGINT', NULL, 'ROW', 1 FROM dataset WHERE code = 'DS_RISK_MATRIX'
UNION ALL SELECT id, 'project_id', '项目 ID', 'DIMENSION', 'BIGINT', NULL, 'ROW', 2 FROM dataset WHERE code = 'DS_RISK_MATRIX'
UNION ALL SELECT id, 'category', '类别', 'DIMENSION', 'VARCHAR', NULL, 'COLUMN', 3 FROM dataset WHERE code = 'DS_RISK_MATRIX'
UNION ALL SELECT id, 'probability', '概率', 'MEASURE', 'INT', 'AVG', NULL, 4 FROM dataset WHERE code = 'DS_RISK_MATRIX'
UNION ALL SELECT id, 'impact', '影响', 'MEASURE', 'INT', 'AVG', NULL, 5 FROM dataset WHERE code = 'DS_RISK_MATRIX'
UNION ALL SELECT id, 'heat_score', '热分数', 'MEASURE', 'INT', 'AVG', NULL, 6 FROM dataset WHERE code = 'DS_RISK_MATRIX'
UNION ALL SELECT id, 'status', '状态', 'DIMENSION', 'VARCHAR', NULL, 'COLUMN', 7 FROM dataset WHERE code = 'DS_RISK_MATRIX'
UNION ALL SELECT id, 'count', '数量', 'MEASURE', 'INT', 'COUNT', NULL, 8 FROM dataset WHERE code = 'DS_RISK_MATRIX';

-- DS_MILESTONE_EVM 字段 (9 个)
INSERT IGNORE INTO dataset_field (dataset_id, field_name, display_name, field_type, data_type, agg_func, dim_role, sort_order)
SELECT id, 'project_id', '项目 ID', 'DIMENSION', 'BIGINT', NULL, 'ROW', 1 FROM dataset WHERE code = 'DS_MILESTONE_EVM'
UNION ALL SELECT id, 'milestone_id', '里程碑 ID', 'DIMENSION', 'BIGINT', NULL, 'ROW', 2 FROM dataset WHERE code = 'DS_MILESTONE_EVM'
UNION ALL SELECT id, 'period', '期间', 'DIMENSION', 'DATE', NULL, 'COLUMN', 3 FROM dataset WHERE code = 'DS_MILESTONE_EVM'
UNION ALL SELECT id, 'planned_value', 'PV', 'MEASURE', 'NUMERIC', 'SUM', NULL, 4 FROM dataset WHERE code = 'DS_MILESTONE_EVM'
UNION ALL SELECT id, 'earned_value', 'EV', 'MEASURE', 'NUMERIC', 'SUM', NULL, 5 FROM dataset WHERE code = 'DS_MILESTONE_EVM'
UNION ALL SELECT id, 'actual_cost', 'AC', 'MEASURE', 'NUMERIC', 'SUM', NULL, 6 FROM dataset WHERE code = 'DS_MILESTONE_EVM'
UNION ALL SELECT id, 'schedule_variance', 'SV', 'MEASURE', 'NUMERIC', 'SUM', NULL, 7 FROM dataset WHERE code = 'DS_MILESTONE_EVM'
UNION ALL SELECT id, 'cost_variance', 'CV', 'MEASURE', 'NUMERIC', 'SUM', NULL, 8 FROM dataset WHERE code = 'DS_MILESTONE_EVM'
UNION ALL SELECT id, 'progress_percent', '进度 %', 'MEASURE', 'INT', 'AVG', NULL, 9 FROM dataset WHERE code = 'DS_MILESTONE_EVM';

-- DS_BUDGET_VS_ACTUAL 字段 (8 个)
INSERT IGNORE INTO dataset_field (dataset_id, field_name, display_name, field_type, data_type, agg_func, dim_role, sort_order)
SELECT id, 'project_id', '项目 ID', 'DIMENSION', 'BIGINT', NULL, 'ROW', 1 FROM dataset WHERE code = 'DS_BUDGET_VS_ACTUAL'
UNION ALL SELECT id, 'period', '期间', 'DIMENSION', 'DATE', NULL, 'COLUMN', 2 FROM dataset WHERE code = 'DS_BUDGET_VS_ACTUAL'
UNION ALL SELECT id, 'category', '类别', 'DIMENSION', 'VARCHAR', NULL, 'ROW', 3 FROM dataset WHERE code = 'DS_BUDGET_VS_ACTUAL'
UNION ALL SELECT id, 'budget', '预算', 'MEASURE', 'NUMERIC', 'SUM', NULL, 4 FROM dataset WHERE code = 'DS_BUDGET_VS_ACTUAL'
UNION ALL SELECT id, 'actual', '实际', 'MEASURE', 'NUMERIC', 'SUM', NULL, 5 FROM dataset WHERE code = 'DS_BUDGET_VS_ACTUAL'
UNION ALL SELECT id, 'variance', '差异', 'MEASURE', 'NUMERIC', 'SUM', NULL, 6 FROM dataset WHERE code = 'DS_BUDGET_VS_ACTUAL'
UNION ALL SELECT id, 'variance_pct', '差异 %', 'MEASURE', 'NUMERIC', 'AVG', NULL, 7 FROM dataset WHERE code = 'DS_BUDGET_VS_ACTUAL'
UNION ALL SELECT id, 'cost_per_hour', '每小时成本', 'MEASURE', 'NUMERIC', 'AVG', NULL, 8 FROM dataset WHERE code = 'DS_BUDGET_VS_ACTUAL;

-- ============================================================
-- §B 8 个角色默认仪表盘 (DASH_*)
-- ============================================================
INSERT IGNORE INTO dashboard (code, name, scope, is_default, is_shared, refresh_interval_sec, status, description)
VALUES
    ('DASH_PMO_DIRECTOR_HOME', 'PMO 总监首页', 'ROLE', 0, 1, 300, 'PUBLISHED',
     'PMO_DIRECTOR 角色默认仪表盘 — 7 KPI + 4 图表 + 3 列表(战略全景)'),
    ('DASH_PMO_ADMIN_HOME', 'PMO 管理员首页', 'ROLE', 0, 1, 300, 'PUBLISHED',
     'PMO_ADMIN 角色默认仪表盘 — 7 KPI + 4 图表 + 3 列表(项目全集)'),
    ('DASH_PMO_HOME', 'PMO 首页', 'ROLE', 0, 1, 300, 'PUBLISHED',
     'PMO 角色默认仪表盘 — 6 KPI + 3 图表 + 2 列表'),
    ('DASH_EXEC_HOME', '高管首页', 'ROLE', 0, 1, 600, 'PUBLISHED',
     'EXEC 角色默认仪表盘 — 5 KPI + 3 图表(决策视图)'),
    ('DASH_DEPT_LEAD_HOME', '部门负责人首页', 'ROLE', 0, 1, 300, 'PUBLISHED',
     'DEPT_LEAD 角色默认仪表盘 — 6 KPI + 3 图表 + 2 列表(部门视角)'),
    ('DASH_PROJECT_LEAD_HOME', '项目负责人首页', 'ROLE', 0, 1, 180, 'PUBLISHED',
     'PROJECT_LEAD 角色默认仪表盘 — 8 KPI + 4 图表 + 3 列表(操作视图)'),
    ('DASH_TEAM_MEMBER_HOME', '团队成员首页', 'ROLE', 0, 1, 180, 'PUBLISHED',
     'TEAM_MEMBER 角色默认仪表盘 — 4 KPI + 2 图表 + 1 列表(任务视角)'),
    ('DASH_TASK_USER_HOME', '任务执行人首页', 'ROLE', 0, 1, 120, 'PUBLISHED',
     'TASK_USER 角色默认仪表盘 — 3 KPI + 1 图表(简洁视图)');

-- ============================================================
-- §C 9 类报表模板 (RPT_*)
-- ============================================================
INSERT IGNORE INTO report_template (code, category, name, dataset_id, format, status, description)
VALUES
    ('RPT_STATUS_WEEKLY', 'STATUS', '项目周报', (SELECT id FROM dataset WHERE code='DS_PROJECT_PROFIT'), 'TABLE', 'PUBLISHED',
     '项目状态周报 — 进度/风险/工时/成本汇总'),
    ('RPT_EVM', 'EVM', 'EVM 挣值分析', (SELECT id FROM dataset WHERE code='DS_MILESTONE_EVM'), 'TABLE', 'PUBLISHED',
     '里程碑 EVM 分析 — PV/EV/AC/SPI/CPI 指标'),
    ('RPT_RISK_HEATMAP', 'RISK', '风险热力图', (SELECT id FROM dataset WHERE code='DS_RISK_MATRIX'), 'TABLE', 'PUBLISHED',
     '风险矩阵 — 概率 × 影响 × 热分数'),
    ('RPT_BUDGET_VS_ACTUAL', 'FINANCE', '预算执行偏差', (SELECT id FROM dataset WHERE code='DS_BUDGET_VS_ACTUAL'), 'TABLE', 'PUBLISHED',
     '预算 vs 实际 — 偏差率 + 趋势'),
    ('RPT_RESOURCE_UTILIZATION', 'RESOURCE', '资源利用率', (SELECT id FROM dataset WHERE code='DS_RESOURCE_UTIL'), 'TABLE', 'PUBLISHED',
     '成员/部门资源利用率排行'),
    ('RPT_QUALITY_DEFECT', 'QUALITY', '质量缺陷报表', NULL, 'TABLE', 'PUBLISHED',
     '质量缺陷统计 — bug/缺陷/回归'),
    ('RPT_PORTFOLIO', 'PORTFOLIO', '项目组合概览', (SELECT id FROM dataset WHERE code='DS_PROJECT_PROFIT'), 'TABLE', 'PUBLISHED',
     '项目组合全景 — 健康度/进度/成本'),
    ('RPT_PERFORMANCE', 'PERFORMANCE', '成员绩效', (SELECT id FROM dataset WHERE code='DS_RESOURCE_UTIL'), 'TABLE', 'PUBLISHED',
     '成员绩效 — 工时/产出/质量'),
    ('RPT_AUDIT_LOG', 'AUDIT', '审计日志', NULL, 'TABLE', 'PUBLISHED',
     '审计日志 — 操作/导出/分享');

-- ============================================================
-- §D 8 角色 → dashboard 映射 (system_config, config_group='REPORTING')
-- ============================================================
INSERT IGNORE INTO system_config (config_key, config_value, value_type, config_group, default_value, description, sort_order, updated_by)
VALUES
    ('role_dashboard.PMO_DIRECTOR',  'DASH_PMO_DIRECTOR_HOME', 'STRING', 'REPORTING', 'DASH_PMO_DIRECTOR_HOME',
     'PMO_DIRECTOR 角色默认 dashboard code', 100, 'system'),
    ('role_dashboard.PMO_ADMIN',     'DASH_PMO_ADMIN_HOME',    'STRING', 'REPORTING', 'DASH_PMO_ADMIN_HOME',
     'PMO_ADMIN 角色默认 dashboard code', 110, 'system'),
    ('role_dashboard.PMO',           'DASH_PMO_HOME',          'STRING', 'REPORTING', 'DASH_PMO_HOME',
     'PMO 角色默认 dashboard code', 120, 'system'),
    ('role_dashboard.EXEC',          'DASH_EXEC_HOME',         'STRING', 'REPORTING', 'DASH_EXEC_HOME',
     'EXEC 角色默认 dashboard code', 130, 'system'),
    ('role_dashboard.DEPT_LEAD',     'DASH_DEPT_LEAD_HOME',    'STRING', 'REPORTING', 'DASH_DEPT_LEAD_HOME',
     'DEPT_LEAD 角色默认 dashboard code', 140, 'system'),
    ('role_dashboard.PROJECT_LEAD',  'DASH_PROJECT_LEAD_HOME', 'STRING', 'REPORTING', 'DASH_PROJECT_LEAD_HOME',
     'PROJECT_LEAD 角色默认 dashboard code', 150, 'system'),
    ('role_dashboard.TEAM_MEMBER',   'DASH_TEAM_MEMBER_HOME',  'STRING', 'REPORTING', 'DASH_TEAM_MEMBER_HOME',
     'TEAM_MEMBER 角色默认 dashboard code', 160, 'system'),
    ('role_dashboard.TASK_USER',     'DASH_TASK_USER_HOME',    'STRING', 'REPORTING', 'DASH_TASK_USER_HOME',
     'TASK_USER 角色默认 dashboard code', 170, 'system');

-- ============================================================
-- §E 4 个全局配置 (报表模块开关)
-- ============================================================
INSERT IGNORE INTO system_config (config_key, config_value, value_type, config_group, default_value, description, sort_order, updated_by)
VALUES
    ('reporting.enabled', 'true', 'BOOLEAN', 'REPORTING', 'true',
     '报表模块开关 (v5.0)', 200, 'system'),
    ('reporting.export.ttl_hours', '24', 'NUMBER', 'REPORTING', '24',
     '导出文件 TTL 小时数 (D7 安全)', 210, 'system'),
    ('reporting.snapshot.cron', '0 0 1 * * ?', 'STRING', 'REPORTING', '0 0 1 * * ?',
     'snapshot 物化 cron (每日 01:00)', 220, 'system'),
    ('reporting.dashboard.default_layout', '{"cols":12,"rowHeight":60}', 'JSON', 'REPORTING', '{"cols":12,"rowHeight":60}',
     '默认 dashboard 布局 (12 列网格)', 230, 'system');
