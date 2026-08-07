-- ============================================================
-- V4.4: F4 预警实体补审计列
-- 修复:AlertEvent / AlertRule 继承 SoftDeletableEntity → AuditableEntity,
--      期望 created_at/updated_at 列;但 V4.3 建表时漏了这 2 列
-- ============================================================

-- alert_event 补审计列 + soft-delete 列
ALTER TABLE alert_event
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by  BIGINT,
    ADD COLUMN IF NOT EXISTS updated_by  BIGINT,
    ADD COLUMN IF NOT EXISTS deleted     BOOLEAN     NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_alert_event_created_at ON alert_event(created_at DESC);

-- alert_rule 补审计列 + soft-delete 列
ALTER TABLE alert_rule
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by  BIGINT,
    ADD COLUMN IF NOT EXISTS updated_by  BIGINT,
    ADD COLUMN IF NOT EXISTS deleted     BOOLEAN     NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_alert_rule_created_at ON alert_rule(created_at DESC);

COMMENT ON COLUMN alert_event.deleted IS 'V4.4 补充:soft-delete (继承 SoftDeletableEntity)';
COMMENT ON COLUMN alert_rule.deleted IS 'V4.4 补充:soft-delete (继承 SoftDeletableEntity)';