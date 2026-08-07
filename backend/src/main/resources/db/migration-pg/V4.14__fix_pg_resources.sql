-- ============================================================
-- V4.14: 修复资源管道 + 商机漏斗 + 多维成本看板的 PG 兼容 (2026-06-15)
-- 1) 补 PG 缺失的 resource_skill 表 (mysql 已有 V4.10)
-- 2) 商机漏斗补齐 business_unit / product_line 表 (PG 早期 ddl-auto 建的不全)
-- ============================================================

-- ① resource_skill 表
CREATE TABLE IF NOT EXISTS resource_skill (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    skill_code  VARCHAR(32)  NOT NULL,
    skill_level SMALLINT     NOT NULL DEFAULT 3,
    certified   SMALLINT     NOT NULL DEFAULT 0,
    cert_date   DATE,
    years_exp   NUMERIC(4,1),
    remark      VARCHAR(256),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_rs_user  ON resource_skill(user_id);
CREATE INDEX IF NOT EXISTS idx_rs_skill ON resource_skill(skill_code);
CREATE INDEX IF NOT EXISTS idx_rs_level ON resource_skill(skill_level);

-- ② 商机相关表的索引 (PG 早期 ddl-auto=update 创建,可能索引不全)
CREATE INDEX IF NOT EXISTS idx_opp_status    ON opportunity(status);
CREATE INDEX IF NOT EXISTS idx_opp_stage     ON opportunity(stage);
CREATE INDEX IF NOT EXISTS idx_opp_owner     ON opportunity(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_opp_dates     ON opportunity(lead_date, expected_close);

CREATE INDEX IF NOT EXISTS idx_opph_opp      ON opportunity_stage_history(opportunity_id);
CREATE INDEX IF NOT EXISTS idx_opph_stage    ON opportunity_stage_history(to_stage);

-- ③ business_unit / product_line 兜底 (PG ddl-auto 时若没建,这里兜)
CREATE TABLE IF NOT EXISTS business_unit (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS product_line (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    bu_id       BIGINT       REFERENCES business_unit(id),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ④ seed: 演示 BU / PL (老 mysql 数据若有,这里 ON CONFLICT 跳过)
INSERT INTO business_unit (code, name, description) VALUES
  ('BU-ENTERPRISE', '企业事业部', '面向中大型企业客户'),
  ('BU-GOV',        '政企事业部', '面向政府/事业单位'),
  ('BU-EDU',        '教育事业部', '面向高校/培训机构')
ON CONFLICT (code) DO NOTHING;

INSERT INTO product_line (code, name, bu_id) VALUES
  ('PL-PMO',  'PMO 治理平台', (SELECT id FROM business_unit WHERE code='BU-ENTERPRISE')),
  ('PL-OA',   '协同 OA',       (SELECT id FROM business_unit WHERE code='BU-ENTERPRISE')),
  ('PL-EDU', '教学管理系统',   (SELECT id FROM business_unit WHERE code='BU-EDU'))
ON CONFLICT (code) DO NOTHING;