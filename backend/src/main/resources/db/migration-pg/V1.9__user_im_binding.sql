-- P2-A IM 通知: 用户 ↔ IM 账号绑定表 (PG)
--
-- 一个用户可在多个 IM 平台有不同 external_user_id。
--  - 钉钉/飞书:external_user_id = 邮箱/手机号(群里能看到)
--  - 企业微信:external_user_id = corp 内的 userid
-- enabled=false 临时禁用某个绑定(离职/换号)
-- channel 编码与 NotificationChannel.Type.code() 对齐(wechat_work/dingtalk/feishu)
CREATE TABLE IF NOT EXISTS user_im_binding (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    channel          VARCHAR(32) NOT NULL,
    external_user_id VARCHAR(128) NOT NULL,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_channel UNIQUE (user_id, channel)
);
CREATE INDEX IF NOT EXISTS ix_im_binding_user ON user_im_binding (user_id);
CREATE INDEX IF NOT EXISTS ix_im_binding_channel_external ON user_im_binding (channel, external_user_id);

-- 注释
COMMENT ON TABLE user_im_binding IS 'P2-A 用户-IM 绑定:通知中心按 channel+user_id 路由到具体 IM 账号';
COMMENT ON COLUMN user_im_binding.channel IS 'wechat_work / dingtalk / feishu';
COMMENT ON COLUMN user_im_binding.external_user_id IS 'IM 平台内的用户标识(企微 userid / 钉钉邮箱 / 飞书邮箱)';
