-- V4.13 立项 SOW 贴文本支持
-- 用途:当 SOW 来自邮件 / 即时通讯 / 内联备注时,允许业务方直接粘贴文本。
--      文本与 SOW 文件两种来源互为补充,AI WBS 生成时任一即可触发。
ALTER TABLE project_initiation
    ADD COLUMN IF NOT EXISTS sow_paste_text TEXT;
COMMENT ON COLUMN project_initiation.sow_paste_text IS 'SOW 贴文本(与 sow 文件互为补充;长度>50KB 截断)';
