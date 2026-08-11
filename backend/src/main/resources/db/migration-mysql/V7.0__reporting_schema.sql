-- ============================================================
-- V7.0 reporting 模块 schema — MySQL 版
-- WP-M7-02 v5 数据模型增量 (8 新表 + 6 表扩展 + 12 索引)
-- 顺序: dataset 先于 dashboard_widget(避免 FK 错误)
-- 与 PG 版 V7.0__reporting_schema.sql 字段 1:1
-- ============================================================

-- ============================================================
-- §2.3 dataset
-- ============================================================
CREATE TABLE IF NOT EXISTS dataset (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,
    name            VARCHAR(128) NOT NULL,
    domain          VARCHAR(32) NOT NULL,
    source_table    VARCHAR(64),
    sql_template    TEXT,
    refresh_policy  VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    last_refresh_at TIMESTAMP NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    description     TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集/指标语义层';

CREATE INDEX idx_dataset_domain ON dataset(domain);
CREATE INDEX idx_dataset_status ON dataset(status);

-- ============================================================
-- §2.4 dataset_field
-- ============================================================
CREATE TABLE IF NOT EXISTS dataset_field (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_id      BIGINT NOT NULL,
    field_name      VARCHAR(64) NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    field_type      VARCHAR(16) NOT NULL,
    data_type       VARCHAR(16) NOT NULL,
    agg_func        VARCHAR(16),
    formula         TEXT,
    dim_role        VARCHAR(16),
    sort_order      INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_dataset_field (dataset_id, field_name),
    CONSTRAINT fk_field_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集字段';

CREATE INDEX idx_dataset_field_dataset ON dataset_field(dataset_id);

-- ============================================================
-- §2.1 dashboard
-- ============================================================
CREATE TABLE IF NOT EXISTS dashboard (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,
    name            VARCHAR(128) NOT NULL,
    scope           VARCHAR(16) NOT NULL,
    scope_id        BIGINT,
    owner_id        BIGINT,
    layout          JSON,
    filters         JSON,
    refresh_interval_sec INT NOT NULL DEFAULT 300,
    is_default      TINYINT(1) NOT NULL DEFAULT 0,
    is_shared       TINYINT(1) NOT NULL DEFAULT 0,
    share_url       VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    description     TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表盘定义';

CREATE INDEX idx_dashboard_scope ON dashboard(scope, scope_id);
CREATE INDEX idx_dashboard_owner ON dashboard(owner_id);
CREATE INDEX idx_dashboard_status ON dashboard(status);

-- ============================================================
-- §2.2 dashboard_widget
-- ============================================================
CREATE TABLE IF NOT EXISTS dashboard_widget (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    dashboard_id    BIGINT NOT NULL,
    widget_type     VARCHAR(16) NOT NULL,
    chart_type      VARCHAR(16),
    title           VARCHAR(128) NOT NULL,
    dataset_id      BIGINT,
    query           JSON,
    config          JSON,
    position        JSON NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_widget_dashboard FOREIGN KEY (dashboard_id) REFERENCES dashboard(id) ON DELETE CASCADE,
    CONSTRAINT fk_widget_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表盘 widget 配置';

CREATE INDEX idx_widget_dashboard ON dashboard_widget(dashboard_id);
CREATE INDEX idx_widget_dataset ON dashboard_widget(dataset_id);

-- ============================================================
-- §2.5 report_template
-- ============================================================
CREATE TABLE IF NOT EXISTS report_template (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,
    category        VARCHAR(32) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    dataset_id      BIGINT,
    format          VARCHAR(16) NOT NULL DEFAULT 'TABLE',
    default_filters JSON,
    layout          JSON,
    schedule_cron   VARCHAR(32),
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    description     TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_template_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表模板';

CREATE INDEX idx_report_template_category ON report_template(category);
CREATE INDEX idx_report_template_status ON report_template(status);

-- ============================================================
-- §2.6 report_export
-- ============================================================
CREATE TABLE IF NOT EXISTS report_export (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         VARCHAR(64) UNIQUE NOT NULL,
    template_id     BIGINT,
    dashboard_id    BIGINT,
    user_id         BIGINT NOT NULL,
    format          VARCHAR(8) NOT NULL,
    params          JSON,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    progress        INT NOT NULL DEFAULT 0,
    file_path       TEXT,
    file_size       BIGINT,
    error_message   TEXT,
    expires_at      TIMESTAMP NULL,
    started_at      TIMESTAMP NULL,
    finished_at     TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_export_template FOREIGN KEY (template_id) REFERENCES report_template(id) ON DELETE SET NULL,
    CONSTRAINT fk_export_dashboard FOREIGN KEY (dashboard_id) REFERENCES dashboard(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出任务';

CREATE INDEX idx_report_export_user ON report_export(user_id);
CREATE INDEX idx_report_export_status ON report_export(status);
CREATE INDEX idx_report_export_expires ON report_export(expires_at);

-- ============================================================
-- §2.7 report_snapshot
-- ============================================================
CREATE TABLE IF NOT EXISTS report_snapshot (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id     BIGINT NOT NULL,
    period          VARCHAR(16) NOT NULL,
    data            JSON NOT NULL,
    row_count       INT NOT NULL,
    file_size       BIGINT,
    status          VARCHAR(16) NOT NULL DEFAULT 'BUILDING',
    built_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stale_at        TIMESTAMP NULL,
    UNIQUE KEY uk_template_period (template_id, period),
    CONSTRAINT fk_snapshot_template FOREIGN KEY (template_id) REFERENCES report_template(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表物化快照';

CREATE INDEX idx_report_snapshot_template ON report_snapshot(template_id);
CREATE INDEX idx_report_snapshot_status ON report_snapshot(status);
CREATE INDEX idx_report_snapshot_period ON report_snapshot(period);

-- ============================================================
-- §2.8 report_subscription
-- ============================================================
CREATE TABLE IF NOT EXISTS report_subscription (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,
    user_id         BIGINT NOT NULL,
    template_id     BIGINT,
    dashboard_id    BIGINT,
    channel_set     VARCHAR(64) NOT NULL,
    cron            VARCHAR(32) NOT NULL,
    recipients      JSON,
    params          JSON,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_run_at     TIMESTAMP NULL,
    next_run_at     TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_subscription_template FOREIGN KEY (template_id) REFERENCES report_template(id) ON DELETE SET NULL,
    CONSTRAINT fk_subscription_dashboard FOREIGN KEY (dashboard_id) REFERENCES dashboard(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅分发';

CREATE INDEX idx_subscription_user ON report_subscription(user_id);
CREATE INDEX idx_subscription_status ON report_subscription(status);
CREATE INDEX idx_subscription_next_run ON report_subscription(next_run_at);

-- ============================================================
-- §3 6 张已有表扩展 (INFORMATION_SCHEMA 守卫)
-- ============================================================

-- §3.1 project + health_score
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.columns WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project' AND COLUMN_NAME = 'health_score');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE project ADD COLUMN health_score INT NULL', 'SELECT ''exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.columns WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project' AND COLUMN_NAME = 'health_score_updated_at');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE project ADD COLUMN health_score_updated_at TIMESTAMP NULL', 'SELECT ''exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- §3.2 milestone + EVM
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.columns WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'milestone' AND COLUMN_NAME = 'planned_value');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE milestone ADD COLUMN planned_value DECIMAL(18,2) NULL', 'SELECT ''exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.columns WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'milestone' AND COLUMN_NAME = 'earned_value');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE milestone ADD COLUMN earned_value DECIMAL(18,2) NULL', 'SELECT ''exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.columns WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'milestone' AND COLUMN_NAME = 'actual_cost');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE milestone ADD COLUMN actual_cost DECIMAL(18,2) NULL', 'SELECT ''exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- §3.3 wbs_task + progress
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.columns WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wbs_task' AND COLUMN_NAME = 'progress_percent');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE wbs_task ADD COLUMN progress_percent DECIMAL(5,2) NULL', 'SELECT ''exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.columns WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wbs_task' AND COLUMN_NAME = 'progress_updated_at');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE wbs_task ADD COLUMN progress_updated_at TIMESTAMP NULL', 'SELECT ''exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- §3.4 risk + heat_score
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.columns WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'risk' AND COLUMN_NAME = 'heat_score');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE risk ADD COLUMN heat_score INT NULL', 'SELECT ''exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.columns WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'risk' AND COLUMN_NAME = 'heat_score_calculated_at');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE risk ADD COLUMN heat_score_calculated_at TIMESTAMP NULL', 'SELECT ''exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- §3.5 app_user + default_dashboard_id
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.columns WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'default_dashboard_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE app_user ADD COLUMN default_dashboard_id BIGINT NULL', 'SELECT ''exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- §3.6 initiation_ai_wbs_draft 复合索引
DROP PROCEDURE IF EXISTS create_idx_ai_draft_project_status;
DELIMITER //
CREATE PROCEDURE create_idx_ai_draft_project_status()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'initiation_ai_wbs_draft' AND INDEX_NAME = 'idx_ai_draft_project_status') THEN
        ALTER TABLE initiation_ai_wbs_draft ADD INDEX idx_ai_draft_project_status (project_id, status);
    END IF;
END //
DELIMITER ;
CALL create_idx_ai_draft_project_status();
DROP PROCEDURE create_idx_ai_draft_project_status;
