-- ============================================================
-- V2.1 BU / PL / 关联产品(MySQL 版 — 与 PG 版同步)
-- ============================================================

-- 1) BU
CREATE TABLE business_unit (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(64) NOT NULL,
    description     VARCHAR(256),
    sort_order      INT NOT NULL DEFAULT 0,
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='业务单元 (BU)';
CREATE INDEX idx_bu_deleted ON business_unit(deleted);

-- 2) PL
CREATE TABLE product_line (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bu_id           BIGINT NOT NULL,
    code            VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(64) NOT NULL,
    description     VARCHAR(256),
    sort_order      INT NOT NULL DEFAULT 0,
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_pl_bu FOREIGN KEY (bu_id) REFERENCES business_unit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='产品线 (PL) — 隶属于 BU';
CREATE INDEX idx_pl_bu ON product_line(bu_id);
CREATE INDEX idx_pl_deleted ON product_line(deleted);

-- 3) 关联产品
CREATE TABLE related_product (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    pl_id           BIGINT NOT NULL,
    code            VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(256),
    version         VARCHAR(32),
    sort_order      INT NOT NULL DEFAULT 0,
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_rp_pl FOREIGN KEY (pl_id) REFERENCES product_line(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='关联产品 — 隶属于 PL';
CREATE INDEX idx_rp_pl ON related_product(pl_id);
CREATE INDEX idx_rp_deleted ON related_product(deleted);

-- 4) project 扩展
ALTER TABLE project
    ADD COLUMN bu_id                 BIGINT NULL,
    ADD COLUMN pl_id                 BIGINT NULL,
    ADD COLUMN related_product_id    BIGINT NULL,
    ADD CONSTRAINT fk_project_bu  FOREIGN KEY (bu_id)              REFERENCES business_unit(id),
    ADD CONSTRAINT fk_project_pl  FOREIGN KEY (pl_id)              REFERENCES product_line(id),
    ADD CONSTRAINT fk_project_rp  FOREIGN KEY (related_product_id) REFERENCES related_product(id);

CREATE INDEX idx_project_bu ON project(bu_id);
CREATE INDEX idx_project_pl ON project(pl_id);
CREATE INDEX idx_project_rp ON project(related_product_id);
