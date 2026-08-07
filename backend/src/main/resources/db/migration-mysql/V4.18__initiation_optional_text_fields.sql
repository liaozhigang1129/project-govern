-- V4.18 修复 background/goals/scope 必填问题 (MySQL 版)
-- 业务诉求: 这三个大文本字段改为可选,避免创建立项时因未填导致 INSERT 失败

ALTER TABLE project_initiation
    MODIFY COLUMN background TEXT NULL COMMENT '项目背景 (业务诉求)',
    MODIFY COLUMN goals      TEXT NULL COMMENT '项目目标',
    MODIFY COLUMN scope      TEXT NULL COMMENT '项目范围';