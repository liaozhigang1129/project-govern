-- ============================================================
-- V2.6 P4 风险管理
-- A.2.1 建表(3 张新表)
-- 框架参考: PMBOK 7 (Risk Management) + ISO 31000
-- 设计原则:
--   - probability × impact 算 score, 自动分 level
--   - 风险与 WBS 任务/里程碑软关联(可选)
--   - 风险历史单独成表(审计追踪)
-- ============================================================

-- ============================================================
-- ① risk —— 风险主表
-- ============================================================
CREATE TABLE risk (
    id                BIGSERIAL PRIMARY KEY,
    project_id        BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,

    code              VARCHAR(32)  NOT NULL,   -- 项目内编号 R-001, R-002 ...
    title             VARCHAR(256) NOT NULL,
    description       TEXT,

    -- 风险分类
    category          VARCHAR(16)  NOT NULL,
    -- TECHNICAL / SCHEDULE / COST / QUALITY / EXTERNAL / ORGANIZATIONAL / OTHER

    -- 概率 (1=极低 2=低 3=中 4=高 5=极高)
    probability       INT          NOT NULL,
    -- 影响 (1=轻微 2=较小 3=中等 4=较大 5=严重)
    impact            INT          NOT NULL,

    -- score = probability * impact, 1-25
    -- level 自动算: 1-4 LOW / 5-9 MEDIUM / 10-15 HIGH / 16-25 CRITICAL
    score             INT          NOT NULL,
    level             VARCHAR(16)  NOT NULL,

    -- 状态
    status            VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    -- OPEN(已识别) / MITIGATING(应对中) / CLOSED(已关闭) / OCCURRED(已发生) / ACCEPTED(已接受)

    -- 负责人
    owner_user_id     BIGINT       REFERENCES app_user(id),

    -- 应对措施
    mitigation        TEXT,        -- 预防/缓解措施
    contingency       TEXT,        -- 应急/兜底措施
    response_strategy VARCHAR(16), -- AVOID / MITIGATE / TRANSFER / ACCEPT / EXPLOIT / ENHANCE / SHARE

    -- 日期
    identified_date   DATE         NOT NULL DEFAULT CURRENT_DATE,
    target_close_date DATE,
    actual_close_date DATE,

    -- 软关联(可选): 关联 WBS 任务 / 里程碑
    related_wbs_task_id   BIGINT REFERENCES wbs_task(id) ON DELETE SET NULL,
    related_milestone_id  BIGINT REFERENCES milestone(id)  ON DELETE SET NULL,

    created_by        BIGINT       REFERENCES app_user(id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_risk_project_code UNIQUE (project_id, code),
    CONSTRAINT ck_risk_probability  CHECK (probability BETWEEN 1 AND 5),
    CONSTRAINT ck_risk_impact       CHECK (impact      BETWEEN 1 AND 5),
    CONSTRAINT ck_risk_score        CHECK (score       BETWEEN 1 AND 25),
    CONSTRAINT ck_risk_category     CHECK (category IN (
        'TECHNICAL','SCHEDULE','COST','QUALITY','EXTERNAL','ORGANIZATIONAL','OTHER'
    )),
    CONSTRAINT ck_risk_status       CHECK (status IN (
        'OPEN','MITIGATING','CLOSED','OCCURRED','ACCEPTED'
    )),
    CONSTRAINT ck_risk_level        CHECK (level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_risk_strategy     CHECK (response_strategy IS NULL OR response_strategy IN (
        'AVOID','MITIGATE','TRANSFER','ACCEPT','EXPLOIT','ENHANCE','SHARE'
    ))
);
COMMENT ON TABLE risk IS '项目风险登记册(Risk Register)';

COMMENT ON COLUMN risk.code              IS '项目内编号,如 R-001';
COMMENT ON COLUMN risk.category          IS '风险分类: TECHNICAL/SCHEDULE/COST/QUALITY/EXTERNAL/ORGANIZATIONAL/OTHER';
COMMENT ON COLUMN risk.probability       IS '发生概率 1-5';
COMMENT ON COLUMN risk.impact            IS '影响程度 1-5';
COMMENT ON COLUMN risk.score             IS '风险分数 = probability × impact, 1-25';
COMMENT ON COLUMN risk.level             IS '风险等级(自动): LOW/MEDIUM/HIGH/CRITICAL';
COMMENT ON COLUMN risk.status            IS '状态: OPEN(已识别)/MITIGATING(应对中)/CLOSED(已关闭)/OCCURRED(已发生)/ACCEPTED(已接受)';
COMMENT ON COLUMN risk.owner_user_id     IS '风险责任人';
COMMENT ON COLUMN risk.mitigation        IS '预防/缓解措施';
COMMENT ON COLUMN risk.contingency       IS '应急/兜底措施';
COMMENT ON COLUMN risk.response_strategy IS '应对策略: AVOID/MITIGATE/TRANSFER/ACCEPT/EXPLOIT/ENHANCE/SHARE';
COMMENT ON COLUMN risk.related_wbs_task_id  IS '关联的 WBS 任务(可选)';
COMMENT ON COLUMN risk.related_milestone_id IS '关联的里程碑(可选)';

CREATE INDEX idx_risk_project    ON risk(project_id);
CREATE INDEX idx_risk_status     ON risk(status);
CREATE INDEX idx_risk_level      ON risk(level);
CREATE INDEX idx_risk_owner      ON risk(owner_user_id);
CREATE INDEX idx_risk_category   ON risk(category);
-- 部分索引: 活跃风险(状态非关闭/已接受)快查
CREATE INDEX idx_risk_active     ON risk(project_id)
    WHERE status NOT IN ('CLOSED','ACCEPTED') AND deleted = FALSE;
CREATE INDEX idx_risk_deleted    ON risk(deleted);


-- ============================================================
-- ② risk_response —— 应对行动(可多个,每个 owner/截止日期独立)
-- ============================================================
CREATE TABLE risk_response (
    id              BIGSERIAL PRIMARY KEY,
    risk_id         BIGINT       NOT NULL REFERENCES risk(id) ON DELETE CASCADE,

    action          VARCHAR(256) NOT NULL,   -- 应对动作描述
    owner_user_id   BIGINT       REFERENCES app_user(id),
    due_date        DATE,
    completed_at    TIMESTAMPTZ,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PLANNED',
    -- PLANNED(已计划) / IN_PROGRESS(执行中) / DONE(已完成) / CANCELLED(取消)
    note            TEXT,

    created_by      BIGINT       REFERENCES app_user(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ck_risk_resp_status CHECK (status IN ('PLANNED','IN_PROGRESS','DONE','CANCELLED'))
);
COMMENT ON TABLE  risk_response IS '风险应对行动(每个 risk 可挂多条)';
COMMENT ON COLUMN risk_response.action IS '应对动作';

CREATE INDEX idx_risk_resp_risk   ON risk_response(risk_id);
CREATE INDEX idx_risk_resp_owner  ON risk_response(owner_user_id);
CREATE INDEX idx_risk_resp_status ON risk_response(status);


-- ============================================================
-- ③ risk_history —— 风险变更历史(审计追踪)
-- ============================================================
CREATE TABLE risk_history (
    id              BIGSERIAL PRIMARY KEY,
    risk_id         BIGINT       NOT NULL REFERENCES risk(id) ON DELETE CASCADE,

    -- 动作类型
    action          VARCHAR(32)  NOT NULL,
    -- CREATED / STATUS_CHANGED / SCORE_CHANGED / OWNER_CHANGED / COMMENTED / RESPONSE_ADDED

    -- 变更前后快照(可选, JSON 文本)
    field_name      VARCHAR(64),                 -- 例: status / score / owner
    old_value       TEXT,
    new_value       TEXT,
    comment         TEXT,                        -- 自由文本

    operator_id     BIGINT       REFERENCES app_user(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- DELETED (新增, 软删风险专用 — 跟 STATUS_CHANGED 区分)
    -- 旧/新值固写 'false' / 'true', 对应 risk.deleted 字段
    CONSTRAINT ck_risk_hist_action CHECK (action IN (
        'CREATED','STATUS_CHANGED','SCORE_CHANGED','OWNER_CHANGED',
        'LEVEL_CHANGED','COMMENTED','RESPONSE_ADDED','RESPONSE_DONE',
        'DELETED'
    ))
);
COMMENT ON TABLE  risk_history IS '风险变更历史(审计追踪)';

CREATE INDEX idx_risk_hist_risk    ON risk_history(risk_id);
CREATE INDEX idx_risk_hist_created ON risk_history(created_at);


-- ============================================================
-- 触发器: updated_at 自动维护
-- ============================================================
CREATE TRIGGER trg_risk_updated_at
    BEFORE UPDATE ON risk
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();
COMMENT ON TRIGGER trg_risk_updated_at ON risk IS 'risk.updated_at 自动维护';

CREATE TRIGGER trg_risk_response_updated_at
    BEFORE UPDATE ON risk_response
    FOR EACH ROW EXECUTE FUNCTION pmo.fn_set_updated_at();
COMMENT ON TRIGGER trg_risk_response_updated_at ON risk_response IS 'risk_response.updated_at 自动维护';


-- ============================================================
-- 视图: 风险矩阵快查(项目级汇总)
-- ============================================================
DROP VIEW IF EXISTS v_risk_matrix;
CREATE OR REPLACE VIEW v_risk_matrix AS
SELECT  project_id,
        category,
        level,
        status,
        COUNT(*) AS count,
        -- 按 (probability, impact) 分桶, 用于热力图渲染
        COUNT(*) FILTER (WHERE probability = 1 AND impact = 1) AS p1i1,
        COUNT(*) FILTER (WHERE probability = 1 AND impact = 2) AS p1i2,
        COUNT(*) FILTER (WHERE probability = 1 AND impact = 3) AS p1i3,
        COUNT(*) FILTER (WHERE probability = 1 AND impact = 4) AS p1i4,
        COUNT(*) FILTER (WHERE probability = 1 AND impact = 5) AS p1i5,
        COUNT(*) FILTER (WHERE probability = 2 AND impact = 1) AS p2i1,
        COUNT(*) FILTER (WHERE probability = 2 AND impact = 2) AS p2i2,
        COUNT(*) FILTER (WHERE probability = 2 AND impact = 3) AS p2i3,
        COUNT(*) FILTER (WHERE probability = 2 AND impact = 4) AS p2i4,
        COUNT(*) FILTER (WHERE probability = 2 AND impact = 5) AS p2i5,
        COUNT(*) FILTER (WHERE probability = 3 AND impact = 1) AS p3i1,
        COUNT(*) FILTER (WHERE probability = 3 AND impact = 2) AS p3i2,
        COUNT(*) FILTER (WHERE probability = 3 AND impact = 3) AS p3i3,
        COUNT(*) FILTER (WHERE probability = 3 AND impact = 4) AS p3i4,
        COUNT(*) FILTER (WHERE probability = 3 AND impact = 5) AS p3i5,
        COUNT(*) FILTER (WHERE probability = 4 AND impact = 1) AS p4i1,
        COUNT(*) FILTER (WHERE probability = 4 AND impact = 2) AS p4i2,
        COUNT(*) FILTER (WHERE probability = 4 AND impact = 3) AS p4i3,
        COUNT(*) FILTER (WHERE probability = 4 AND impact = 4) AS p4i4,
        COUNT(*) FILTER (WHERE probability = 4 AND impact = 5) AS p4i5,
        COUNT(*) FILTER (WHERE probability = 5 AND impact = 1) AS p5i1,
        COUNT(*) FILTER (WHERE probability = 5 AND impact = 2) AS p5i2,
        COUNT(*) FILTER (WHERE probability = 5 AND impact = 3) AS p5i3,
        COUNT(*) FILTER (WHERE probability = 5 AND impact = 4) AS p5i4,
        COUNT(*) FILTER (WHERE probability = 5 AND impact = 5) AS p5i5
FROM risk
WHERE deleted = FALSE
GROUP BY project_id, category, level, status;
COMMENT ON VIEW v_risk_matrix IS '风险矩阵(5x5 热力图) — 按项目/分类/等级/状态聚合';


-- ============================================================
-- 视图: 风险健康度(项目级快查 KPI, 给 PMO 仪表盘用)
-- ============================================================
DROP VIEW IF EXISTS v_risk_health;
CREATE OR REPLACE VIEW v_risk_health AS
SELECT  project_id,
        COUNT(*)                                          AS total_count,
        COUNT(*) FILTER (WHERE status NOT IN ('CLOSED','ACCEPTED'))        AS active_count,
        COUNT(*) FILTER (WHERE level = 'CRITICAL' AND status NOT IN ('CLOSED','ACCEPTED')) AS critical_active,
        COUNT(*) FILTER (WHERE level = 'HIGH'     AND status NOT IN ('CLOSED','ACCEPTED')) AS high_active,
        COUNT(*) FILTER (WHERE status = 'OCCURRED')                        AS occurred_count,
        MAX(score) FILTER (WHERE status NOT IN ('CLOSED','ACCEPTED'))      AS max_active_score
FROM risk
WHERE deleted = FALSE
GROUP BY project_id;
COMMENT ON VIEW v_risk_health IS '项目风险健康度(KPI 聚合)';
