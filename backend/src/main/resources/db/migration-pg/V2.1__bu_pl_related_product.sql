-- ============================================================
-- V2.1 BU / PL / 关联产品 字典 + 项目扩展
-- ============================================================
--
-- 业务说明:
--  - BU(Business Unit, 业务单元): 公司层一级业务划分(如"金融事业部"/"政企事业部")
--  - PL(Product Line, 产品线): BU 下的产品线(如金融 BU 下的"支付产品线")
--  - 关联产品(RelatedProduct): 实际产品名(如"银企通"),可跨 PL 复用
--  - 三个都是字典驱动(便于后台 CRUD),项目表存字典 id
--  - BU/PL/关联产品一一对应,可建表时用外键约束保持一致

-- 1) BU 字典
CREATE TABLE business_unit (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) UNIQUE NOT NULL,    -- FIN / GOV / MKT ...
    name            VARCHAR(64) NOT NULL,            -- 金融事业部
    description     VARCHAR(256),
    sort_order      INT NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_bu_deleted ON business_unit(deleted);
COMMENT ON TABLE business_unit IS '业务单元 (BU)';

-- 2) PL 字典
CREATE TABLE product_line (
    id              BIGSERIAL PRIMARY KEY,
    bu_id           BIGINT NOT NULL REFERENCES business_unit(id),   -- 隶属 BU
    code            VARCHAR(32) UNIQUE NOT NULL,    -- FIN-PAY / FIN-CORE ...
    name            VARCHAR(64) NOT NULL,            -- 支付产品线
    description     VARCHAR(256),
    sort_order      INT NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_pl_bu ON product_line(bu_id);
CREATE INDEX idx_pl_deleted ON product_line(deleted);
COMMENT ON TABLE product_line IS '产品线 (PL) — 隶属于 BU';

-- 3) 关联产品 字典
CREATE TABLE related_product (
    id              BIGSERIAL PRIMARY KEY,
    pl_id           BIGINT NOT NULL REFERENCES product_line(id),    -- 隶属 PL
    code            VARCHAR(32) UNIQUE NOT NULL,    -- BANKLINK ...
    name            VARCHAR(128) NOT NULL,           -- 银企通
    description     VARCHAR(256),
    version         VARCHAR(32),                     -- 选填:v2.3
    sort_order      INT NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_rp_pl ON related_product(pl_id);
CREATE INDEX idx_rp_deleted ON related_product(deleted);
COMMENT ON TABLE related_product IS '关联产品 — 隶属于 PL';

-- 4) project 表加 3 个外键(均为可选)
ALTER TABLE project
    ADD COLUMN bu_id                 BIGINT REFERENCES business_unit(id),
    ADD COLUMN pl_id                 BIGINT REFERENCES product_line(id),
    ADD COLUMN related_product_id    BIGINT REFERENCES related_product(id);

CREATE INDEX idx_project_bu  ON project(bu_id);
CREATE INDEX idx_project_pl  ON project(pl_id);
CREATE INDEX idx_project_rp  ON project(related_product_id);

-- 5) updated_at trigger
DROP TRIGGER IF EXISTS trg_bu_updated_at ON business_unit;
CREATE TRIGGER trg_bu_updated_at BEFORE UPDATE ON business_unit
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_pl_updated_at ON product_line;
CREATE TRIGGER trg_pl_updated_at BEFORE UPDATE ON product_line
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_rp_updated_at ON related_product;
CREATE TRIGGER trg_rp_updated_at BEFORE UPDATE ON related_product
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();
