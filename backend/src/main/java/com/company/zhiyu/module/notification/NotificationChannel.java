package com.company.zhiyu.module.notification;

/**
 * 通知通道抽象(P2-A)。
 *
 * 实现:
 *  - EmailChannel(已有 MailService 包装)
 *  - WechatWorkChannel(企业微信自建应用)
 *  - DingTalkChannel(钉钉群机器人)
 *  - FeishuChannel(飞书群机器人)
 *
 * send() 失败必须吞异常(打 warn log),不允许抛到上游业务。
 */
public interface NotificationChannel {

    /** 通道类型枚举 */
    enum Type {
        EMAIL("email"),
        WECHAT_WORK("wechat_work"),
        DINGTALK("dingtalk"),
        FEISHU("feishu");

        private final String code;
        Type(String code) { this.code = code; }
        public String code() { return code; }

        public static Type fromCode(String s) {
            if (s == null) return null;
            for (Type t : values()) if (t.code.equalsIgnoreCase(s)) return t;
            return null;
        }
    }

    /** 通道类型标识 */
    Type type();

    /** 是否启用(检查 properties + binding 完整性) */
    boolean isEnabled();

    /**
     * 发送通知(必须不抛异常)。
     * @return true=发送成功 false=失败(已 log warn,不影响上游)
     */
    boolean send(NotificationMessage message);
}
