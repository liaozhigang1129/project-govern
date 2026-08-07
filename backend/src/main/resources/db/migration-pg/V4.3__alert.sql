-- ============================================================
-- V4.3: F4 预警引擎 - 6 规则 + 事件记录
-- 2 表: alert_rule (规则定义) + alert_event (事件记录)
-- 6 种子规则: BUDGET_EXCEED/HOURS_OVER/CONTRACT_BALANCE/PROJECT_STALE/ROLE_DEFAULT/PAYMENT_OVERDUE
-- 幂等 (IF NOT EXISTS): 可重复跑
-- ============================================================

-- 字典 1: 规则类型
CREATE TABLE IF NOT EXISTS alert_type_def (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    default_threshold NUMERIC(14,4),
    default_comparison VARCHAR(8) NOT NULL DEFAULT 'GT',  -- GT/LT/EQ
    target_type VARCHAR(16) NOT NULL,                    -- PROJECT/USER/CONTRACT/SYSTEM
    description TEXT
);

INSERT INTO alert_type_def (code, name, default_threshold, default_comparison, target_type, description) VALUES
  ('BUDGET_EXCEED',      '项目预算超支',     0.90,  'GT',  'PROJECT',  '项目实际成本 / 预算 ≥ 阈值'),
  ('HOURS_OVER',         '单人月工时超限',   200,   'GT',  'USER',     '单用户月度工时 ≥ 阈值'),
  ('CONTRACT_BALANCE',   '合同余额不足',     0.10,  'LT',  'CONTRACT', '合同剩余金额 / 总额 ≤ 阈值'),
  ('PROJECT_STALE',      '项目停滞',         14,    'GT',  'PROJECT',  '项目无工时天数 ≥ 阈值'),
  ('ROLE_DEFAULT',       '角色档 fallback',  1,     'GT',  'SYSTEM',   '上月有工时但走 default_hourly_rate 的人数'),
  ('PAYMENT_OVERDUE',    '发票逾期未付',     30,    'GT',  'INVOICE',  '发票创建到当前天数 ≥ 阈值')
ON CONFLICT (code) DO NOTHING;

-- 字典 2: 事件严重程度
CREATE TABLE IF NOT EXISTS alert_severity_def (
    code VARCHAR(16) PRIMARY KEY,
    level INT NOT NULL,  -- 1=LOW 2=MEDIUM 3=HIGH 4=CRITICAL
    name VARCHAR(32) NOT NULL
);
INSERT INTO alert_severity_def (code, level, name) VALUES
  ('LOW',      1, '低'),
  ('MEDIUM',   2, '中'),
  ('HIGH',     3, '高'),
  ('CRITICAL', 4, '严重')
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- 表 1: alert_rule (规则定义)
-- ============================================================
CREATE TABLE IF NOT EXISTS alert_rule (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    type_code       VARCHAR(32)  NOT NULL REFERENCES alert_type_def(code),
    threshold       NUMERIC(14,4) NOT NULL,
    comparison      VARCHAR(8)   NOT NULL DEFAULT 'GT' CHECK (comparison IN ('GT','LT','EQ')),
    severity        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' REFERENCES alert_severity_def(code),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    target_filter   VARCHAR(256),              -- 可选: 限定 project_id / user_id / department_id
    notify_emails   VARCHAR(512),
    webhook_url     VARCHAR(512),
    description     TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_alert_rule_type_code ON alert_rule(type_code);
CREATE INDEX IF NOT EXISTS idx_alert_rule_enabled  ON alert_rule(enabled);

-- ============================================================
-- 表 2: alert_event (事件记录)
-- ============================================================
CREATE TABLE IF NOT EXISTS alert_event (
    id               BIGSERIAL PRIMARY KEY,
    rule_id          BIGINT       NOT NULL REFERENCES alert_rule(id) ON DELETE CASCADE,
    triggered_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    severity         VARCHAR(16)  NOT NULL REFERENCES alert_severity_def(code),
    message          TEXT         NOT NULL,
    target_type      VARCHAR(16)  NOT NULL,   -- PROJECT/USER/CONTRACT/INVOICE/SYSTEM
    target_id        BIGINT,
    target_label     VARCHAR(256),
    actual_value     NUMERIC(14,4),
    threshold_value  NUMERIC(14,4),
    status           VARCHAR(16)  NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','ACKNOWLEDGED','RESOLVED','SUPPRESSED')),
    acknowledged_by  BIGINT,
    acknowledged_at  TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    notify_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING' CHECK (notify_status IN ('PENDING','SENT','FAILED','SKIPPED')),
    notify_sent_at   TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_alert_event_rule_id         ON alert_event(rule_id);
CREATE INDEX IF NOT EXISTS idx_alert_event_status          ON alert_event(status);
CREATE INDEX IF NOT EXISTS idx_alert_event_severity        ON alert_event(severity);
CREATE INDEX IF NOT EXISTS idx_alert_event_triggered_at    ON alert_event(triggered_at DESC);
CREATE INDEX IF NOT EXISTS idx_alert_event_target          ON alert_event(target_type, target_id);

-- ============================================================
-- 6 种子规则
-- ============================================================
INSERT INTO alert_rule (code, name, type_code, threshold, comparison, severity, enabled, description, notify_emails)
SELECT v.code, v.name, v.type_code, v.threshold, v.comparison, v.severity, v.enabled, v.description, v.emails
FROM (VALUES
  ('RULE_BUDGET_90',    '项目预算超 90% 警告', 'BUDGET_EXCEED',     0.90, 'GT', 'HIGH',     TRUE, '项目实际成本达到预算 90% 时预警', 'pmo@company.com,finance@company.com'),
  ('RULE_HOURS_200',    '单人月工时超 200h',   'HOURS_OVER',        200,  'GT', 'MEDIUM',   TRUE, '单人月度工时超过 200h',           'pm@company.com'),
  ('RULE_CONTRACT_10',  '合同余额不足 10%',    'CONTRACT_BALANCE',  0.10, 'LT', 'HIGH',     TRUE, '合同剩余金额不足 10%',           'finance@company.com'),
  ('RULE_STALE_14D',    '项目 14 天无工时',    'PROJECT_STALE',     14,   'GT', 'MEDIUM',   TRUE, '项目连续 14 天无工时',           'pm@company.com'),
  ('RULE_ROLE_DEFAULT', '角色档 fallback 异常','ROLE_DEFAULT',      1,    'GT', 'LOW',      TRUE, '上月有工时但角色不在 6 档',      'finance@company.com'),
  ('RULE_PAYMENT_30D',  '发票逾期 30 天未付',  'PAYMENT_OVERDUE',   30,   'GT', 'CRITICAL', TRUE, '发票创建超过 30 天仍未付款',     'finance@company.com,cfo@company.com')
) AS v(code, name, type_code, threshold, comparison, severity, enabled, description, emails)
WHERE NOT EXISTS (SELECT 1 FROM alert_rule WHERE alert_rule.code = v.code);

COMMENT ON TABLE alert_rule  IS 'F4 预警规则定义 (6 类)';
COMMENT ON TABLE alert_event IS 'F4 预警事件记录';
COMMENT ON COLUMN alert_event.status IS 'NEW=新触发 / ACKNOWLEDGED=已确认 / RESOLVED=已解决 / SUPPRESSED=已抑制';
COMMENT ON COLUMN alert_event.notify_status IS 'PENDING=待发 / SENT=已发 / FAILED=失败 / SKIPPED=跳过';
