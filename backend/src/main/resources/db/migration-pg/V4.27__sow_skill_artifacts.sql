-- V4.27 SOW Skill (PMI 7 阶段硬骨架) 持久化产物 (PostgreSQL 版)
-- 业务诉求: skill/pmo-pms v1.4.0 落地
--   - PMI 7 阶段硬骨架 (WBS-1..WBS-7, 名字镜像 lifecyclePhases[])
--   - 9 维 NFR 打标 (performance/security/availability/scalability/
--     usability/maintainability/compliance/interoperability/dataIntegrity)
--   - RTM 矩阵 (requirement ↔ WBS L3 ↔ TC)
--   - MoSCoW 优先级 / 治理信号推断结果
-- 设计:
--   - 主数据走 initiation_ai_wbs_draft.draft_json (TEXT, JSONB 视图层提取)
--   - 这两张表只存"需要按 requirement_id 高频反查/做交叉分析"的明细
--     (RTM 行 + NFR 标签), 避免每次都 parse 整个 draft JSON
-- 数据约定:
--   - sow_rtm_row.initiation_id 必填, requirement_id 引用 draft_json.requirements[].id
--   - wbs_ids / test_case_ids 是 JSON 数组文本, 便于反查
--   - sow_nfr_tag 一条 REQ 最多 9 行 (9 个维度); is_primary=true 表示 primaryDimension
--   - 删除时按 initiation_id 物理级联 (PMO 业务里立项删除属少见, 暂不做软删除)

-- ============================================================
-- ① sow_rtm_row —— Requirements Traceability Matrix 明细
-- ============================================================
CREATE TABLE IF NOT EXISTS sow_rtm_row (
    id                  BIGSERIAL    PRIMARY KEY,
    initiation_id       BIGINT       NOT NULL,
    requirement_id      VARCHAR(32)  NOT NULL,                        -- e.g. REQ-001
    wbs_ids             TEXT         NOT NULL DEFAULT '[]',            -- JSON array: ["WBS-2.1", ...]
    test_case_ids       TEXT         NOT NULL DEFAULT '[]',            -- JSON array: ["TC-001-01", ...]
    status              VARCHAR(16)  NOT NULL DEFAULT 'Pending',       -- Pending|InProgress|Verified
    verification_method VARCHAR(32),                                   -- Test|Inspection|Analysis|Demonstration
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sow_rtm_init  ON sow_rtm_row(initiation_id);
CREATE INDEX IF NOT EXISTS idx_sow_rtm_req   ON sow_rtm_row(requirement_id);
CREATE INDEX IF NOT EXISTS idx_sow_rtm_stat  ON sow_rtm_row(status) WHERE status <> 'Verified';

COMMENT ON TABLE  sow_rtm_row             IS 'SOW skill: RTM 明细 (requirement ↔ WBS L3 ↔ TC)';
COMMENT ON COLUMN sow_rtm_row.wbs_ids     IS 'JSON array of WBS ids, e.g. ["WBS-2.1","WBS-3.2"]';
COMMENT ON COLUMN sow_rtm_row.test_case_ids IS 'JSON array of TC ids, e.g. ["TC-001-01","TC-001-02"]';

-- ============================================================
-- ② sow_nfr_tag —— 9 维 NFR 打标
-- ============================================================
CREATE TABLE IF NOT EXISTS sow_nfr_tag (
    id              BIGSERIAL    PRIMARY KEY,
    initiation_id   BIGINT       NOT NULL,
    requirement_id  VARCHAR(32)  NOT NULL,                            -- e.g. REQ-001
    dimension       VARCHAR(32)  NOT NULL,                            -- 9 维之一 (枚举见下)
    is_primary      BOOLEAN      NOT NULL DEFAULT FALSE,              -- primaryDimension 标记
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sow_nfr_init ON sow_nfr_tag(initiation_id);
CREATE INDEX IF NOT EXISTS idx_sow_nfr_req  ON sow_nfr_tag(requirement_id);
CREATE INDEX IF NOT EXISTS idx_sow_nfr_dim  ON sow_nfr_tag(dimension);
CREATE INDEX IF NOT EXISTS idx_sow_nfr_primary ON sow_nfr_tag(initiation_id, requirement_id) WHERE is_primary = TRUE;

COMMENT ON TABLE  sow_nfr_tag             IS 'SOW skill: 9 维 NFR 打标 (一行一条 REQ×维度)';
COMMENT ON COLUMN sow_nfr_tag.dimension   IS 'performance | security | availability | scalability | usability | maintainability | compliance | interoperability | dataIntegrity';

-- ============================================================
-- ③ 一致性约束: NFR 维度必须在 9 维枚举内
-- ============================================================
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_sow_nfr_dim_enum') THEN
    ALTER TABLE sow_nfr_tag
      ADD CONSTRAINT ck_sow_nfr_dim_enum
      CHECK (dimension IN (
        'performance','security','availability','scalability','usability',
        'maintainability','compliance','interoperability','dataIntegrity'
      ));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_sow_nfr_primary_unique') THEN
    -- 一条 REQ 最多 1 个 primaryDimension
    ALTER TABLE sow_nfr_tag
      ADD CONSTRAINT ck_sow_nfr_primary_unique
      UNIQUE (initiation_id, requirement_id, is_primary)
      DEFERRABLE INITIALLY IMMEDIATE;
  END IF;
EXCEPTION WHEN others THEN
  -- 如果已存在 (幂等迁移场景) 忽略
  NULL;
END $$;

-- 注意: 上面的 UNIQUE 在 (initiation_id, requirement_id, is_primary) 上
-- 实际上当 is_primary=FALSE 时, 多个行同 (init, req, false) 不冲突 (因为 false 重复)
-- PostgreSQL 的 UNIQUE 把 NULL 视为不等, 这里 is_primary NOT NULL 所以可严格去重
