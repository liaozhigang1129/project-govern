-- V4.27 SOW Skill (PMI 7 阶段硬骨架) 持久化产物 (MySQL 版)
-- 业务诉求 + 设计: 同 migration-pg/V4.27__sow_skill_artifacts.sql

-- ============================================================
-- ① sow_rtm_row —— RTM 明细
-- ============================================================
CREATE TABLE IF NOT EXISTS sow_rtm_row (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    initiation_id       BIGINT       NOT NULL,
    requirement_id      VARCHAR(32)  NOT NULL,
    wbs_ids             TEXT         NOT NULL,
    test_case_ids       TEXT         NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'Pending',
    verification_method VARCHAR(32)  NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sow_rtm_init (initiation_id),
    KEY idx_sow_rtm_req  (requirement_id),
    KEY idx_sow_rtm_stat (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SOW skill: RTM 明细 (requirement ↔ WBS L3 ↔ TC)';

-- ============================================================
-- ② sow_nfr_tag —— 9 维 NFR 打标
-- ============================================================
CREATE TABLE IF NOT EXISTS sow_nfr_tag (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    initiation_id   BIGINT       NOT NULL,
    requirement_id  VARCHAR(32)  NOT NULL,
    dimension       VARCHAR(32)  NOT NULL,
    is_primary      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sow_nfr_init (initiation_id),
    KEY idx_sow_nfr_req  (requirement_id),
    KEY idx_sow_nfr_dim  (dimension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SOW skill: 9 维 NFR 打标 (一行一条 REQ×维度)';

-- ============================================================
-- ③ 一致性约束
-- ============================================================
-- MySQL 8+ 才支持 CHECK, 旧版忽略 (应用层兜底)
-- ALTER TABLE sow_nfr_tag
--   ADD CONSTRAINT ck_sow_nfr_dim_enum
--   CHECK (dimension IN ('performance','security','availability','scalability','usability',
--                         'maintainability','compliance','interoperability','dataIntegrity'));
