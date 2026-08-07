-- ============================================================
-- V4.3: F4 预警引擎 - 6 规则 + 事件记录 (MySQL 版)
-- 与 PG 版 V4.3 同构, 差异:
--   - BIGSERIAL → BIGINT AUTO_INCREMENT
--   - TIMESTAMPTZ → DATETIME(6)
--   - BOOLEAN → TINYINT(1)
--   - NUMERIC → DECIMAL
--   - 去掉 CHECK / ON CONFLICT / IF NOT EXISTS (MySQL 8.0 兼容写法)
-- ============================================================

-- 字典 1: 规则类型
CREATE TABLE IF NOT EXISTS alert_type_def (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    default_threshold DECIMAL(14,4),
    default_comparison VARCHAR(8) NOT NULL DEFAULT 'GT',
    target_type VARCHAR(16) NOT NULL,
    description TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预警规则类型字典';

INSERT IGNORE INTO alert_type_def (code, name, default_threshold, default_comparison, target_type, description) VALUES
  ('BUDGET_EXCEED',    '项目预算超支',     0.90, 'GT', 'PROJECT', '项目实际成本 / 预算 ≥ 阈值'),
  ('HOURS_OVER',       '单人月工时超限',   200,  'GT', 'USER',    '单用户月度工时 ≥ 阈值'),
  ('CONTRACT_BALANCE', '合同余额不足',     0.10, 'LT', 'CONTRACT','合同剩余金额 / 总额 ≤ 阈值'),
  ('PROJECT_STALE',    '项目停滞',         14,   'GT', 'PROJECT', '项目无工时天数 ≥ 阈值'),
  ('ROLE_DEFAULT',     '角色档 fallback',  1,    'GT', 'SYSTEM',  '上月有工时但走 default_hourly_rate 的人数'),
  ('PAYMENT_OVERDUE',  '发票逾期未付',     30,   'GT', 'INVOICE', '发票创建到当前天数 ≥ 阈值');

-- 字典 2: 事件严重程度
CREATE TABLE IF NOT EXISTS alert_severity_def (
    code VARCHAR(16) PRIMARY KEY,
    level INT NOT NULL,
    name VARCHAR(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预警严重程度字典';

INSERT IGNORE INTO alert_severity_def (code, level, name) VALUES
  ('LOW',      1, '低'),
  ('MEDIUM',   2, '中'),
  ('HIGH',     3, '高'),
  ('CRITICAL', 4, '严重');

-- 表 1: alert_rule
CREATE TABLE IF NOT EXISTS alert_rule (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    type_code       VARCHAR(32)  NOT NULL,
    threshold       DECIMAL(14,4) NOT NULL,
    comparison      VARCHAR(8)   NOT NULL DEFAULT 'GT',
    severity        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    target_filter   VARCHAR(256),
    notify_emails   VARCHAR(512),
    webhook_url     VARCHAR(512),
    description     TEXT,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_alert_rule_type     FOREIGN KEY (type_code) REFERENCES alert_type_def(code),
    CONSTRAINT fk_alert_rule_severity FOREIGN KEY (severity)  REFERENCES alert_severity_def(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F4 预警规则定义';

CREATE INDEX idx_alert_rule_type_code ON alert_rule(type_code);
CREATE INDEX idx_alert_rule_enabled   ON alert_rule(enabled);

-- 表 2: alert_event
CREATE TABLE IF NOT EXISTS alert_event (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rule_id          BIGINT       NOT NULL,
    triggered_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    severity         VARCHAR(16)  NOT NULL,
    message          TEXT         NOT NULL,
    target_type      VARCHAR(16)  NOT NULL,
    target_id        BIGINT,
    target_label     VARCHAR(256),
    actual_value     DECIMAL(14,4),
    threshold_value  DECIMAL(14,4),
    status           VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    acknowledged_by  BIGINT,
    acknowledged_at  DATETIME(6),
    resolved_at      DATETIME(6),
    notify_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    notify_sent_at   DATETIME(6),
    CONSTRAINT fk_alert_event_rule     FOREIGN KEY (rule_id)  REFERENCES alert_rule(id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_event_severity FOREIGN KEY (severity) REFERENCES alert_severity_def(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='F4 预警事件记录';

CREATE INDEX idx_alert_event_rule_id      ON alert_event(rule_id);
CREATE INDEX idx_alert_event_status       ON alert_event(status);
CREATE INDEX idx_alert_event_severity     ON alert_event(severity);
CREATE INDEX idx_alert_event_triggered_at ON alert_event(triggered_at);
CREATE INDEX idx_alert_event_target       ON alert_event(target_type, target_id);

-- 6 种子规则 (用 INSERT ... SELECT WHERE NOT EXISTS 模拟幂等)
INSERT INTO alert_rule (code, name, type_code, threshold, comparison, severity, enabled, description, notify_emails)
SELECT * FROM (SELECT 'RULE_BUDGET_90'    AS code, '项目预算超 90% 警告' AS name, 'BUDGET_EXCEED'    AS type_code, 0.90 AS threshold, 'GT' AS comparison, 'HIGH'     AS severity, 1 AS enabled, '项目实际成本达到预算 90% 时预警' AS description, 'pmo@company.com,finance@company.com' AS notify_emails) t
WHERE NOT EXISTS (SELECT 1 FROM alert_rule r WHERE r.code = t.code);

INSERT INTO alert_rule (code, name, type_code, threshold, comparison, severity, enabled, description, notify_emails)
SELECT * FROM (SELECT 'RULE_HOURS_200'    AS code, '单人月工时超 200h'   AS name, 'HOURS_OVER'       AS type_code, 200  AS threshold, 'GT' AS comparison, 'MEDIUM'   AS severity, 1 AS enabled, '单人月度工时超过 200h'           AS description, 'pm@company.com' AS notify_emails) t
WHERE NOT EXISTS (SELECT 1 FROM alert_rule r WHERE r.code = t.code);

INSERT INTO alert_rule (code, name, type_code, threshold, comparison, severity, enabled, description, notify_emails)
SELECT * FROM (SELECT 'RULE_CONTRACT_10'  AS code, '合同余额不足 10%'    AS name, 'CONTRACT_BALANCE' AS type_code, 0.10 AS threshold, 'LT' AS comparison, 'HIGH'     AS severity, 1 AS enabled, '合同剩余金额不足 10%'           AS description, 'finance@company.com' AS notify_emails) t
WHERE NOT EXISTS (SELECT 1 FROM alert_rule r WHERE r.code = t.code);

INSERT INTO alert_rule (code, name, type_code, threshold, comparison, severity, enabled, description, notify_emails)
SELECT * FROM (SELECT 'RULE_STALE_14D'    AS code, '项目 14 天无工时'    AS name, 'PROJECT_STALE'    AS type_code, 14   AS threshold, 'GT' AS comparison, 'MEDIUM'   AS severity, 1 AS enabled, '项目连续 14 天无工时'           AS description, 'pm@company.com' AS notify_emails) t
WHERE NOT EXISTS (SELECT 1 FROM alert_rule r WHERE r.code = t.code);

INSERT INTO alert_rule (code, name, type_code, threshold, comparison, severity, enabled, description, notify_emails)
SELECT * FROM (SELECT 'RULE_ROLE_DEFAULT' AS code, '角色档 fallback 异常' AS name, 'ROLE_DEFAULT'     AS type_code, 1    AS threshold, 'GT' AS comparison, 'LOW'      AS severity, 1 AS enabled, '上月有工时但角色不在 6 档'      AS description, 'finance@company.com' AS notify_emails) t
WHERE NOT EXISTS (SELECT 1 FROM alert_rule r WHERE r.code = t.code);

INSERT INTO alert_rule (code, name, type_code, threshold, comparison, severity, enabled, description, notify_emails)
SELECT * FROM (SELECT 'RULE_PAYMENT_30D'  AS code, '发票逾期 30 天未付'  AS name, 'PAYMENT_OVERDUE'  AS type_code, 30   AS threshold, 'GT' AS comparison, 'CRITICAL' AS severity, 1 AS enabled, '发票创建超过 30 天仍未付款'     AS description, 'finance@company.com,cfo@company.com' AS notify_emails) t
WHERE NOT EXISTS (SELECT 1 FROM alert_rule r WHERE r.code = t.code);
