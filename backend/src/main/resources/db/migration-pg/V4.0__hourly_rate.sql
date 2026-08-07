-- ============================================================
-- V4.0b: 财务精细化成本管控 - 工时→成本引擎 (PG 版, 修订)
-- 设计变更: hourly_rate (V2.5 已占用) → hourly_rate_v4 (V4.0 新表)
--  - V2.5 hourly_rate 是按 role 唯一, 没有 user_id
--  - V4.0 需要按 (user_id, role_code, month) 调档, 独立成 hourly_rate_v4
--
-- 步骤:
--   1) app_user.default_hourly_rate 兜底  (幂等)
--   2) 新建 hourly_rate_v4 表
--   3) role_cost_default (6 角色档字典)
-- ============================================================

-- ① app_user.default_hourly_rate 兜底 (幂等)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='app_user' AND column_name='default_hourly_rate'
  ) THEN
    ALTER TABLE app_user
      ADD COLUMN default_hourly_rate NUMERIC(10,2) NOT NULL DEFAULT 0;
  END IF;
END $$;

COMMENT ON COLUMN app_user.default_hourly_rate IS
  '默认时薪(元/h), 优先级最低, 被 hourly_rate_v4 覆盖';

-- ② hourly_rate_v4 (V4.0 新表, 不动 V2.5 hourly_rate)
CREATE TABLE IF NOT EXISTS hourly_rate_v4 (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_code       VARCHAR(32) NOT NULL REFERENCES role_cost_default(code),
    rate            NUMERIC(10,2) NOT NULL,
    effective_month DATE NOT NULL,
    end_month       DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT REFERENCES app_user(id),
    remark          VARCHAR(256),
    UNIQUE (user_id, role_code, effective_month)
);

CREATE INDEX IF NOT EXISTS idx_hourly_rate_v4_user_month
  ON hourly_rate_v4(user_id, effective_month DESC);

CREATE INDEX IF NOT EXISTS idx_hourly_rate_v4_role_month
  ON hourly_rate_v4(role_code, effective_month DESC);

COMMENT ON TABLE hourly_rate_v4 IS
  'V4.0 cost-control: 人×月度×角色档时薪 (优先级最高, 覆盖 app_user.default_hourly_rate)';

-- ③ role_cost_default (6 角色档字典) — V4.0 新增
CREATE TABLE IF NOT EXISTS role_cost_default (
    code        VARCHAR(32) PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    rate        NUMERIC(10,2) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0
);

INSERT INTO role_cost_default (code, name, rate, sort_order) VALUES
    ('ARCH',   '架构师',  800.00, 1),
    ('DEV',    '开发',    600.00, 2),
    ('TEST',   '测试',    500.00, 3),
    ('PM',     'PM',      700.00, 4),
    ('BA',     'BA',      600.00, 5),
    ('OPS',    '运维',    550.00, 6)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    rate = EXCLUDED.rate,
    sort_order = EXCLUDED.sort_order;

COMMENT ON TABLE role_cost_default IS '角色档默认时薪(财务可调)';
