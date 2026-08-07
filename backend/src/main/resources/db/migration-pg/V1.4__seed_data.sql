-- ============================================================
-- V1.4 种子数据: 字典 + 演示账号
-- ============================================================

-- 5 个内置角色
INSERT INTO role (code, name, description) VALUES
    ('PM',         '项目经理',     '维护本项目的里程碑与进度'),
    ('DEPT_LEAD',  '部门负责人',   '审批本部门提交的立项'),
    ('PMO_ADMIN',  'PMO管理员',   '立项复核 + 治理配置'),
    ('EXEC',       '分管副总',    '最终审批'),
    ('VIEWER',     '只读访客',    '查看项目/报表,无写权限');
SELECT setval(pg_get_serial_sequence('role','id'), (SELECT MAX(id) FROM role));

-- 项目类型
INSERT INTO project_type (code, name, description) VALUES
    ('DELIVERY',      '客户交付',  '为外部客户交付的项目'),
    ('SELF_RD',       '自研产品',  '公司自主研发产品'),
    ('INNER_PRODUCT', '内部产品',  '内部工具/系统建设'),
    ('RD',            '研发探索',  '预研、技术储备项目');
SELECT setval(pg_get_serial_sequence('project_type','id'), (SELECT MAX(id) FROM project_type));

-- 项目状态
INSERT INTO project_status (code, name, is_terminal) VALUES
    ('DRAFT',     '草稿',     FALSE),
    ('PENDING',   '待立项',   FALSE),
    ('ACTIVE',    '执行中',   FALSE),
    ('SUSPENDED', '已暂停',   FALSE),
    ('CLOSED',    '已结项',   TRUE),
    ('REJECTED',  '已驳回',   TRUE);
SELECT setval(pg_get_serial_sequence('project_status','id'), (SELECT MAX(id) FROM project_status));

-- 健康度
INSERT INTO health_level (code, name, color_hex) VALUES
    ('GREEN',  '正常', '#67C23A'),
    ('YELLOW', '关注', '#E6A23C'),
    ('RED',    '严重', '#F56C6C');
SELECT setval(pg_get_serial_sequence('health_level','id'), (SELECT MAX(id) FROM health_level));

-- 立项状态
INSERT INTO initiation_status (code, name, sort_order, is_terminal) VALUES
    ('DRAFT',          '草稿',     0, FALSE),
    ('PENDING',        '审批中',   1, FALSE),
    ('DEPT_APPROVED',  '部门通过', 2, FALSE),
    ('PMO_APPROVED',   'PMO通过',  3, FALSE),
    ('EXEC_APPROVED',  '已批准',   4, TRUE),
    ('REJECTED',       '已驳回',   5, TRUE),
    ('SUPPLEMENT',     '需补充',   6, FALSE);
SELECT setval(pg_get_serial_sequence('initiation_status','id'), (SELECT MAX(id) FROM initiation_status));

-- 审批步骤
INSERT INTO approval_step (code, name, sequence, description) VALUES
    ('DEPT_LEAD', '部门负责人审批', 1, '部门负责人初审'),
    ('PMO_ADMIN', 'PMO管理员复核', 2, 'PMO 治理视角复核'),
    ('EXEC',      '分管副总审批',  3, '最终审批');
SELECT setval(pg_get_serial_sequence('approval_step','id'), (SELECT MAX(id) FROM approval_step));

-- 里程碑状态
INSERT INTO milestone_status (code, name, is_terminal) VALUES
    ('PENDING',     '未开始',   FALSE),
    ('IN_PROGRESS', '进行中',   FALSE),
    ('COMPLETED',   '已完成',   TRUE),
    ('DELAYED',     '已延期',   FALSE);
SELECT setval(pg_get_serial_sequence('milestone_status','id'), (SELECT MAX(id) FROM milestone_status));

-- 演示部门
INSERT INTO department (name, code, parent_id, sort_order) VALUES
    ('总公司',     'ROOT',    NULL, 0),
    ('研发部',     'RD',      1,    1),
    ('产品部',     'PD',      1,    2),
    ('交付部',     'DL',      1,    3);
SELECT setval(pg_get_serial_sequence('department','id'), (SELECT MAX(id) FROM department));

-- 演示用户(密码统一: pmo123, BCrypt hash)
-- 注: 实际项目中密码应在应用层首次登录时强制修改
-- hash 来自 BCrypt strength 10: 'pmo123'
INSERT INTO app_user (username, password_hash, full_name, email, department_id, primary_role_id, job_title) VALUES
    ('admin',   '$2a$10$j/Vd3KOxoOA0NgbdMa23r.ZN5Ka7sIDJQRsMQCQEwLBkfxdnsLU4G', '系统管理员',  'admin@company.com',  1, (SELECT id FROM role WHERE code='PMO_ADMIN'), 'PMO主任'),
    ('pm_zhang','$2a$10$j/Vd3KOxoOA0NgbdMa23r.ZN5Ka7sIDJQRsMQCQEwLBkfxdnsLU4G', '张三',        'zhang@company.com',  2, (SELECT id FROM role WHERE code='PM'),         '项目经理'),
    ('pm_li',   '$2a$10$j/Vd3KOxoOA0NgbdMa23r.ZN5Ka7sIDJQRsMQCQEwLBkfxdnsLU4G', '李四',        'li@company.com',     2, (SELECT id FROM role WHERE code='PM'),         '项目经理'),
    ('lead_wu', '$2a$10$j/Vd3KOxoOA0NgbdMa23r.ZN5Ka7sIDJQRsMQCQEwLBkfxdnsLU4G', '吴经理',      'wu@company.com',     2, (SELECT id FROM role WHERE code='DEPT_LEAD'),  '研发经理'),
    ('vp_chen', '$2a$10$j/Vd3KOxoOA0NgbdMa23r.ZN5Ka7sIDJQRsMQCQEwLBkfxdnsLU4G', '陈副总',      'chen@company.com',   1, (SELECT id FROM role WHERE code='EXEC'),       '分管副总'),
    ('viewer',  '$2a$10$j/Vd3KOxoOA0NgbdMa23r.ZN5Ka7sIDJQRsMQCQEwLBkfxdnsLU4G', '只读访客',    'viewer@company.com', 1, (SELECT id FROM role WHERE code='VIEWER'),     '审计员');
SELECT setval(pg_get_serial_sequence('app_user','id'), (SELECT MAX(id) FROM app_user));
