-- ============================================================
-- V4.19 — 修复 SOW 上传: content_type 列宽 64 → 255
-- 原因: 浏览器 multipart/form-data 的 Content-Type 形如
--   "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW"
-- 长度 70~200 字节, 旧 VARCHAR(64) 会触发 MySQL "Data too long"。
-- ============================================================

ALTER TABLE initiation_sow_file
    ALTER COLUMN content_type TYPE VARCHAR(255);