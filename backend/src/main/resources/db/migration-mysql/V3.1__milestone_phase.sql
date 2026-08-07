-- ============================================================
-- V3.1: 里程碑阶段分类 + 阶段字典 (MySQL 版, 幂等)
-- 与 PG 版同构, 差异:
--   - 去掉 IF NOT EXISTS, 用存储过程
--   - DO $$ 块 → 拆成存储过程
--   - ALTER COLUMN SET NOT NULL → MODIFY COLUMN
-- ============================================================
SET @db = DATABASE();

CREATE TABLE IF NOT EXISTS milestone_phase (
    id          bigint PRIMARY KEY,
    code        varchar(32) UNIQUE NOT NULL,
    name        varchar(64) NOT NULL,
    sort_order  int  NOT NULL,
    description text
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='里程碑阶段字典';

INSERT IGNORE INTO milestone_phase (id, code, name, sort_order, description) VALUES
    (1, 'INITIATION',  '立项',     1, '项目立项阶段'),
    (2, 'REQUIREMENT', '需求',     2, '需求调研 / 需求评审 / 需求基线'),
    (3, 'DESIGN',      '设计',     3, '方案设计 / 架构设计 / 详细设计'),
    (4, 'DEVELOPMENT', '开发',     4, '后端开发 / 前端开发 / 开发完成'),
    (5, 'TESTING',     '测试',     5, '单元测试 / 集成测试 / UAT'),
    (6, 'DEPLOY',      '上线运维', 6, '正式上线 / 系统上线'),
    (7, 'MAINTENANCE', '维保',     7, '验收交付 / 维保期');

-- milestone 加 phase_id
SET @stmt = (SELECT IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='milestone' AND COLUMN_NAME='phase_id')=0,
  'ALTER TABLE milestone ADD COLUMN phase_id BIGINT NULL', 'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='milestone' AND INDEX_NAME='idx_milestone_phase');
SET @stmt = IF(@idx=0, 'CREATE INDEX idx_milestone_phase ON milestone(phase_id)', 'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 旧项目无 phase_id,回填
UPDATE milestone SET phase_id = CASE
    WHEN sequence = 1 THEN 1 WHEN sequence = 2 THEN 2
    WHEN sequence = 3 THEN 3 WHEN sequence = 4 THEN 4
    WHEN sequence = 5 THEN 5 WHEN sequence = 6 THEN 6
    ELSE 7
END
WHERE phase_id IS NULL;

-- 删老 unique uk_milestone_seq, 避免重排时冲突
SET @con = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='milestone' AND CONSTRAINT_NAME='uk_milestone_seq');
SET @stmt = IF(@con>0, 'ALTER TABLE milestone DROP INDEX uk_milestone_seq', 'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 同 phase 内 sequence 重排 (1..N) - MySQL 用变量
SET @rn := 0;
SET @cur_pid := -1;
SET @cur_phase := -1;
UPDATE milestone m
JOIN (
    SELECT id, @rn := IF(@cur_pid = project_id AND @cur_phase <=> phase_id, @rn + 1, 1) AS rn,
           @cur_pid := project_id, @cur_phase := phase_id
    FROM (SELECT id, project_id, phase_id FROM milestone WHERE deleted = 0 ORDER BY project_id, phase_id, id) tmp
) sub ON m.id = sub.id
SET m.sequence = sub.rn;

-- 换唯一约束
SET @con = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='milestone' AND CONSTRAINT_NAME='uq_milestone_project_phase');
SET @stmt = IF(@con=0, 'ALTER TABLE milestone ADD CONSTRAINT uq_milestone_project_phase UNIQUE (project_id, phase_id, sequence)', 'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 删老的 (可能不存在)
SET @con = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='milestone' AND CONSTRAINT_NAME='milestone_project_id_sequence_key');
SET @stmt = IF(@con>0, 'ALTER TABLE milestone DROP CONSTRAINT milestone_project_id_sequence_key', 'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- phase_id NOT NULL (注意: 只有当所有行 phase_id 都非空才安全, 否则保留 NULL)
SET @has_null = (SELECT COUNT(*) FROM milestone WHERE phase_id IS NULL);
SET @stmt = IF(@has_null=0, 'ALTER TABLE milestone MODIFY COLUMN phase_id BIGINT NOT NULL', 'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
