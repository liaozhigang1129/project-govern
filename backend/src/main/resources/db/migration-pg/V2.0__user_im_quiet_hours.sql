-- P2 #2 勿扰时段: 每用户多窗口 (PostgreSQL)
--
-- 与 MySQL 同构,字段顺序保持一致。
--
-- 注意: Flyway 用 '' 转义单引号,直接在常量里写正则要双重转义。
-- 这里改成 VARCHAR(5) 即可,正则校验放到应用层 (DTO @Pattern)。

CREATE TABLE IF NOT EXISTS user_im_quiet_hours (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    start_time  VARCHAR(5) NOT NULL,
    end_time    VARCHAR(5) NOT NULL,
    timezone    VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_quiet_hours_start_len CHECK (length(start_time) = 5),
    CONSTRAINT ck_quiet_hours_end_len   CHECK (length(end_time) = 5)
);

CREATE INDEX IF NOT EXISTS ix_quiet_hours_user ON user_im_quiet_hours(user_id, enabled);
