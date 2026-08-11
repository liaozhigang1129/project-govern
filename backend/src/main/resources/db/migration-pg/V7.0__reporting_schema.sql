-- ============================================================
-- V7.0 reporting 模块 schema — PG 版
-- WP-M7-02 v5 数据模型增量 (8 新表 + 6 表扩展 + 12 索引)
-- 顺序: dataset 先于 dashboard_widget(避免 FK 错误)
-- 对应 spec: docs/specs/reporting.md / reporting-api.md
-- 对应 ADR-005 D4(导出) / D5(数据集) / D7(安全)
-- ============================================================

-- ============================================================
-- §2.3 dataset (指标语义层) — 必须先于 dashboard_widget/report_template
-- ============================================================
CREATE TABLE IF NOT EXISTS dataset (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,
    name            VARCHAR(128) NOT NULL,
    domain          VARCHAR(32) NOT NULL,
    source_table    VARCHAR(64),
    sql_template    TEXT,
    refresh_policy  VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    last_refresh_at TIMESTAMPTZ,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    description     TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE dataset IS '数据集/指标语义层 (WP-M7-02 D5)';

-- §5 04
CREATE INDEX IF NOT EXISTS idx_dataset_domain ON dataset(domain);
CREATE INDEX IF NOT EXISTS idx_dataset_status ON dataset(status);

-- ============================================================
-- §2.4 dataset_field
-- ============================================================
CREATE TABLE IF NOT EXISTS dataset_field (
    id              BIGSERIAL PRIMARY KEY,
    dataset_id      BIGINT NOT NULL REFERENCES dataset(id) ON DELETE CASCADE,
    field_name      VARCHAR(64) NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    field_type      VARCHAR(16) NOT NULL,
    data_type       VARCHAR(16) NOT NULL,
    agg_func        VARCHAR(16),
    formula         TEXT,
    dim_role        VARCHAR(16),
    sort_order      INT NOT NULL DEFAULT 0,
    UNIQUE (dataset_id, field_name)
);
COMMENT ON TABLE dataset_field IS '数据集字段 (维度+度量)';

CREATE INDEX IF NOT EXISTS idx_dataset_field_dataset ON dataset_field(dataset_id);

-- ============================================================
-- §2.1 dashboard
-- ============================================================
CREATE TABLE IF NOT EXISTS dashboard (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,
    name            VARCHAR(128) NOT NULL,
    scope           VARCHAR(16) NOT NULL,
    scope_id        BIGINT,
    owner_id        BIGINT,
    layout          JSON,
    filters         JSON,
    refresh_interval_sec INT NOT NULL DEFAULT 300,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    is_shared       BOOLEAN NOT NULL DEFAULT FALSE,
    share_url       VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE dashboard IS '仪表盘定义 (WP-M7-02)';

-- §5 01 / 02
CREATE INDEX IF NOT EXISTS idx_dashboard_scope ON dashboard(scope, scope_id);
CREATE INDEX IF NOT EXISTS idx_dashboard_owner ON dashboard(owner_id);
CREATE INDEX IF NOT EXISTS idx_dashboard_status ON dashboard(status);

-- ============================================================
-- §2.2 dashboard_widget (依赖 dashboard + dataset)
-- ============================================================
CREATE TABLE IF NOT EXISTS dashboard_widget (
    id              BIGSERIAL PRIMARY KEY,
    dashboard_id    BIGINT NOT NULL REFERENCES dashboard(id) ON DELETE CASCADE,
    widget_type     VARCHAR(16) NOT NULL,
    chart_type      VARCHAR(16),
    title           VARCHAR(128) NOT NULL,
    dataset_id      BIGINT REFERENCES dataset(id) ON DELETE SET NULL,
    query           JSON,
    config          JSON,
    position        JSON NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE dashboard_widget IS '仪表盘 widget 配置 (WP-M7-02)';

-- 03
CREATE INDEX IF NOT EXISTS idx_widget_dashboard ON dashboard_widget(dashboard_id);
CREATE INDEX IF NOT EXISTS idx_widget_dataset ON dashboard_widget(dataset_id);

-- ============================================================
-- §2.5 report_template (依赖 dataset)
-- ============================================================
CREATE TABLE IF NOT EXISTS report_template (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,
    category        VARCHAR(32) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    dataset_id      BIGINT REFERENCES dataset(id) ON DELETE SET NULL,
    format          VARCHAR(16) NOT NULL DEFAULT 'TABLE',
    default_filters JSON,
    layout          JSON,
    schedule_cron   VARCHAR(32),
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    description     TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE report_template IS '报表模板 (9 大类,WP-M7-02)';

-- 06
CREATE INDEX IF NOT EXISTS idx_report_template_category ON report_template(category);
CREATE INDEX IF NOT EXISTS idx_report_template_status ON report_template(status);

-- ============================================================
-- §2.6 report_export (D4 决策)
-- ============================================================
CREATE TABLE IF NOT EXISTS report_export (
    id              BIGSERIAL PRIMARY KEY,
    task_id         VARCHAR(64) UNIQUE NOT NULL,
    template_id     BIGINT REFERENCES report_template(id) ON DELETE SET NULL,
    dashboard_id    BIGINT REFERENCES dashboard(id) ON DELETE SET NULL,
    user_id         BIGINT NOT NULL,
    format          VARCHAR(8) NOT NULL,
    params          JSON,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    progress        INT NOT NULL DEFAULT 0,
    file_path       TEXT,
    file_size       BIGINT,
    error_message   TEXT,
    expires_at      TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE report_export IS '导出任务 (D4 4 格式, D7 TTL)';

-- 07 / 08 / 09
CREATE INDEX IF NOT EXISTS idx_report_export_user ON report_export(user_id);
CREATE INDEX IF NOT EXISTS idx_report_export_status ON report_export(status);
CREATE INDEX IF NOT EXISTS idx_report_export_expires ON report_export(expires_at);

-- ============================================================
-- §2.7 report_snapshot (D5)
-- ============================================================
CREATE TABLE IF NOT EXISTS report_snapshot (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT NOT NULL REFERENCES report_template(id) ON DELETE CASCADE,
    period          VARCHAR(16) NOT NULL,
    data            JSON NOT NULL,
    row_count       INT NOT NULL,
    file_size       BIGINT,
    status          VARCHAR(16) NOT NULL DEFAULT 'BUILDING',
    built_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    stale_at        TIMESTAMPTZ,
    UNIQUE (template_id, period)
);
COMMENT ON TABLE report_snapshot IS '报表物化快照 (D5 预聚合)';

-- 10 / 11
CREATE INDEX IF NOT EXISTS idx_report_snapshot_template ON report_snapshot(template_id);
CREATE INDEX IF NOT EXISTS idx_report_snapshot_status ON report_snapshot(status);
CREATE INDEX IF NOT EXISTS idx_report_snapshot_period ON report_snapshot(period);

-- ============================================================
-- §2.8 report_subscription (D6)
-- ============================================================
CREATE TABLE IF NOT EXISTS report_subscription (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) UNIQUE NOT NULL,
    user_id         BIGINT NOT NULL,
    template_id     BIGINT REFERENCES report_template(id) ON DELETE SET NULL,
    dashboard_id    BIGINT REFERENCES dashboard(id) ON DELETE SET NULL,
    channel_set     VARCHAR(64) NOT NULL,
    cron            VARCHAR(32) NOT NULL,
    recipients      JSON,
    params          JSON,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_run_at     TIMESTAMPTZ,
    next_run_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE report_subscription IS '订阅分发 (D6 3 通道)';

-- 12
CREATE INDEX IF NOT EXISTS idx_subscription_user ON report_subscription(user_id);
CREATE INDEX IF NOT EXISTS idx_subscription_status ON report_subscription(status);
CREATE INDEX IF NOT EXISTS idx_subscription_next_run ON report_subscription(next_run_at);

-- ============================================================
-- §3 6 张已有表扩展
-- ============================================================

-- §3.1 project + health_score
ALTER TABLE project ADD COLUMN IF NOT EXISTS health_score INT;
ALTER TABLE project ADD COLUMN IF NOT EXISTS health_score_updated_at TIMESTAMPTZ;

-- §3.2 milestone + EVM
ALTER TABLE milestone ADD COLUMN IF NOT EXISTS planned_value DECIMAL(18,2);
ALTER TABLE milestone ADD COLUMN IF NOT EXISTS earned_value  DECIMAL(18,2);
ALTER TABLE milestone ADD COLUMN IF NOT EXISTS actual_cost   DECIMAL(18,2);

-- §3.3 wbs_task + progress_percent
ALTER TABLE wbs_task ADD COLUMN IF NOT EXISTS progress_percent NUMERIC(5,2);
ALTER TABLE wbs_task ADD COLUMN IF NOT EXISTS progress_updated_at TIMESTAMPTZ;

-- §3.4 risk + heat_score
ALTER TABLE risk ADD COLUMN IF NOT EXISTS heat_score INT;
ALTER TABLE risk ADD COLUMN IF NOT EXISTS heat_score_calculated_at TIMESTAMPTZ;

-- §3.5 app_user + default_dashboard_id
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS default_dashboard_id BIGINT REFERENCES dashboard(id) ON DELETE SET NULL;

-- §3.6 initiation_ai_wbs_draft 复合索引 (兼容干净 DB: 缺列则加, 不存在索引则创建)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes WHERE indexname='idx_ai_draft_project_status'
  ) THEN
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_name='initiation_ai_wbs_draft' AND column_name='project_id'
    ) THEN
      ALTER TABLE initiation_ai_wbs_draft ADD COLUMN project_id BIGINT;
    END IF;
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
      WHERE table_name='initiation_ai_wbs_draft' AND column_name='status'
    ) THEN
      ALTER TABLE initiation_ai_wbs_draft ADD COLUMN status VARCHAR(32);
    END IF;
    CREATE INDEX IF NOT EXISTS idx_ai_draft_project_status
      ON initiation_ai_wbs_draft(project_id, status);
  END IF;
END $$;
