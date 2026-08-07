-- V3.1: 里程碑阶段分类 + 阶段字典 (幂等版,可重复跑)
-- 背景: 原本 sequence 字段是手填的任意数字,出现 7355/30471 等无意义大数
-- 改造: 引入阶段字典 (milestone_phase), 7 阶段 (立项/需求/设计/开发/测试/上线运维/维保)

CREATE TABLE IF NOT EXISTS milestone_phase (
    id          bigint PRIMARY KEY,
    code        varchar(32) UNIQUE NOT NULL,
    name        varchar(64) NOT NULL,
    sort_order  int  NOT NULL,
    description text
);

INSERT INTO milestone_phase (id, code, name, sort_order, description) VALUES
    (1, 'INITIATION',  '立项',     1, '项目立项阶段'),
    (2, 'REQUIREMENT', '需求',     2, '需求调研 / 需求评审 / 需求基线'),
    (3, 'DESIGN',      '设计',     3, '方案设计 / 架构设计 / 详细设计'),
    (4, 'DEVELOPMENT', '开发',     4, '后端开发 / 前端开发 / 开发完成'),
    (5, 'TESTING',     '测试',     5, '单元测试 / 集成测试 / UAT'),
    (6, 'DEPLOY',      '上线运维', 6, '正式上线 / 系统上线'),
    (7, 'MAINTENANCE', '维保',     7, '验收交付 / 维保期')
ON CONFLICT (id) DO NOTHING;

ALTER TABLE milestone ADD COLUMN IF NOT EXISTS phase_id bigint;
CREATE INDEX IF NOT EXISTS idx_milestone_phase ON milestone(phase_id);

-- 旧项目无 phase_id,回填
UPDATE milestone SET phase_id = CASE
    WHEN sequence = 1 THEN 1 WHEN sequence = 2 THEN 2
    WHEN sequence = 3 THEN 3 WHEN sequence = 4 THEN 4
    WHEN sequence = 5 THEN 5 WHEN sequence = 6 THEN 6
    ELSE 7
END
WHERE phase_id IS NULL;

-- 同 phase 内 sequence 重排 (1..N)
UPDATE milestone m SET sequence = sub.rn
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY project_id, phase_id ORDER BY id) AS rn FROM milestone WHERE deleted=false) sub
WHERE m.id=sub.id AND m.deleted=false;

-- 换唯一约束
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uq_milestone_project_phase') THEN
    ALTER TABLE milestone ADD CONSTRAINT uq_milestone_project_phase UNIQUE (project_id, phase_id, sequence);
  END IF;
  IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname='milestone_project_id_sequence_key') THEN
    ALTER TABLE milestone DROP CONSTRAINT milestone_project_id_sequence_key;
  END IF;
END $$;

ALTER TABLE milestone ALTER COLUMN phase_id SET NOT NULL;
