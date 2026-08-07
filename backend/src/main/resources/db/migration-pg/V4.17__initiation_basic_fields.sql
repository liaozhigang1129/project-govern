-- V4.17 立项基础信息补全
-- 业务诉求: 立项 Step 1 除标题/背景/目标/范围/合同外, 还要带齐
--   所属部门 / 项目经理 / 项目类型 / 项目级别 / 预估毛利率 / 入场时间 / 计划上线时间 / 项目结束时间
-- 注意:
--   - department_id 已在 project_initiation 存在 → 沿用
--   - planned_start / planned_end 已在 project_initiation 存在 → planned_end 即"项目结束时间"
--   - planned_start 在语义上是"入场时间" (kickoff) → 加注释, 字段名不动
--   - 新增 planned_launch_date "计划上线时间" (UAT→灰度→全量的目标日)
--   - 新增 pm_user_id "项目经理" (与 applicant 申请人不一定相同; 申请人是 SR, PM 可能晚定)
--   - 新增 project_type_code "项目类型" (与 project_type 字典对齐)
--   - 新增 project_level_code "项目级别" (新增字典, 与 project_type 平行)
--   - 新增 expected_gross_margin_pct "预估毛利率" (0~100, %)
-- 设计: 立项 / 项目两张表都加这些字段, createProjectFromInitiation 同步拷贝。

-- 1) 立项表加列
ALTER TABLE project_initiation
    ADD COLUMN IF NOT EXISTS pm_user_id              BIGINT,
    ADD COLUMN IF NOT EXISTS project_type_code        VARCHAR(32),
    ADD COLUMN IF NOT EXISTS project_level_code       VARCHAR(32),
    ADD COLUMN IF NOT EXISTS expected_gross_margin_pct NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS planned_launch_date      DATE;

-- 2) 注释
COMMENT ON COLUMN project_initiation.pm_user_id                IS '项目经理 (与 applicant 申请人可不同; 立项后正式 PM)';
COMMENT ON COLUMN project_initiation.project_type_code         IS '项目类型 code (DELIVERY/SELF_RD/INNER_PRODUCT/RD)';
COMMENT ON COLUMN project_initiation.project_level_code        IS '项目级别 code (S/A/B/C)';
COMMENT ON COLUMN project_initiation.expected_gross_margin_pct IS '预估毛利率 %, 0~100';
COMMENT ON COLUMN project_initiation.planned_launch_date       IS '计划上线时间 (UAT/灰度→全量目标日)';
COMMENT ON COLUMN project_initiation.planned_start             IS '入场时间 (kickoff)';
COMMENT ON COLUMN project_initiation.planned_end               IS '项目结束时间';

-- 3) FK / 索引
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'project_initiation_pm_user_id_fkey') THEN
        ALTER TABLE project_initiation
            ADD CONSTRAINT project_initiation_pm_user_id_fkey
            FOREIGN KEY (pm_user_id) REFERENCES app_user(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_initiation_pm ON project_initiation(pm_user_id);
CREATE INDEX IF NOT EXISTS idx_initiation_project_level ON project_initiation(project_level_code);

-- 4) 项目表同步加列 (用于 createProjectFromInitiation 落库)
ALTER TABLE project
    ADD COLUMN IF NOT EXISTS project_level_code       VARCHAR(32),
    ADD COLUMN IF NOT EXISTS expected_gross_margin_pct NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS planned_launch_date      DATE;

COMMENT ON COLUMN project.project_level_code        IS '项目级别 code (S/A/B/C)';
COMMENT ON COLUMN project.expected_gross_margin_pct IS '预估毛利率 %, 0~100';
COMMENT ON COLUMN project.planned_launch_date       IS '计划上线时间';
COMMENT ON COLUMN project.pm_user_id                IS '项目经理 (PM)';
COMMENT ON COLUMN project.plan_start_date           IS '入场时间 (kickoff)';
COMMENT ON COLUMN project.plan_end_date             IS '项目结束时间';

-- 5) 新增项目级别字典 (与 project_type 平行, 同样用 code+name)
CREATE TABLE IF NOT EXISTS project_level (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32) NOT NULL UNIQUE,
    name        VARCHAR(64) NOT NULL,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    description VARCHAR(256)
);

INSERT INTO project_level(code, name, sort_order, description) VALUES
    ('S', 'S 级 - 战略级', 10, '公司战略级 / 千万级以上 / 跨 BU 协同项目'),
    ('A', 'A 级 - 重点级', 20, 'BU 重点项目 / 百万级以上'),
    ('B', 'B 级 - 标准级', 30, '常规交付项目'),
    ('C', 'C 级 - 轻量级', 40, '小项目 / 内部工具 / PoC')
ON CONFLICT (code) DO NOTHING;

-- 6) 列注释同步到 project_level
COMMENT ON TABLE  project_level                IS '项目级别字典 (S/A/B/C)';
COMMENT ON COLUMN project_level.id             IS '项目级别 ID';
COMMENT ON COLUMN project_level.code           IS '级别编码';
COMMENT ON COLUMN project_level.name           IS '级别名称';
COMMENT ON COLUMN project_level.sort_order     IS '排序';
COMMENT ON COLUMN project_level.description    IS '说明';
