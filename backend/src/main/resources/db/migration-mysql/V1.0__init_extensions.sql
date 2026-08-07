-- ============================================================
-- V1.0 初始化: 通用 updated_at 触发器(MySQL 8 方言)
-- ============================================================
-- MySQL 不需要 pgcrypto / SCHEMA / extension
-- 用 BEFORE UPDATE 触发器为每张表内嵌 updated_at 维护逻辑
-- (在 V1.3 末尾逐表挂触发器)

-- 业务库 project_govern 已由 docker-compose.yml 创建,这里不需要 CREATE DATABASE

-- 注意: MySQL 8.0 不支持 CREATE OR REPLACE FUNCTION 与 schema 绑定
-- 我们直接在每张表的触发器里写 SET NEW.updated_at = NOW(3),不再用共享函数
