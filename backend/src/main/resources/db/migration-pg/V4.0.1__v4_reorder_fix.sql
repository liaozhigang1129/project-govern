-- ============================================================
-- V4.0.1 V4.0 步骤重排 fix — role_cost_default 必须先于 hourly_rate_v4 创建
-- 原 V4.0 步骤 ② 引用 role_cost_default(code) 但步骤 ③ 才创建,导致 FK 错误
-- 本迁移:先把 V4.0 标记 success(在 history 表),然后 V4.0.1 创建缺失的 role_cost_default
-- ============================================================

-- 如果 V4.0 已运行但失败,history 表里没有 V4.0 — 检查并补
-- 这里只补 role_cost_default 表(其他 V4.0 步骤已 rollback,需要单独跑)

-- 0. 创建 role_cost_default (从 V4.0 步骤 ③ 提取)
CREATE TABLE IF NOT EXISTS role_cost_default (
    code        VARCHAR(32) PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    rate        NUMERIC(10,2) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0
);

INSERT INTO role_cost_default (code, name, rate, sort_order) VALUES
    ('ARCH',   '架构师',  800.00, 1),
    ('PM',     '项目经理', 600.00, 2),
    ('DEV',    '开发',   500.00, 3),
    ('TEST',   '测试',   450.00, 4),
    ('UI',     'UI',    450.00, 5),
    ('OPS',    '运维',   400.00, 6)
ON CONFLICT (code) DO NOTHING;

-- 1. 创建 hourly_rate_v4 (从 V4.0 步骤 ② 提取)
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

-- 2. app_user.default_hourly_rate 兜底
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
