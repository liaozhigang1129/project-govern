-- ============================================================
-- V1.0 初始化: 扩展、Schema、触发器、序列
-- ============================================================

-- pgcrypto: gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 业务 Schema(便于未来多租户/模块隔离)
CREATE SCHEMA IF NOT EXISTS pmo;

-- 通用 updated_at 触发器函数
CREATE OR REPLACE FUNCTION pmo.fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION pmo.fn_set_updated_at() IS '通用 updated_at 自动维护';
