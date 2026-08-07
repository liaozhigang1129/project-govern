package com.company.zhiyu.module.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 通知相关配置。
 *
 * 示例 application.yml:
 * <pre>
 * pmo:
 *   mail:
 *     enabled: true                     # 总开关
 *     from: "知驭 ZhiYu <noreply@zhiyu.local>"
 *     cc-pmo: true                     # 是否抄送所有 PMO_ADMIN
 *     async: true                       # 是否异步发送(失败不阻塞主业务)
 *     approve-step-emails:              # 各级审批人邮箱(开发兜底;生产应从 user.email 拉)
 *       DEPT_LEAD: lead_wu@company.com
 *       PMO_ADMIN: admin@company.com
 *       EXEC: vp_chen@company.com
 * </pre>
 */
@ConfigurationProperties(prefix = "pmo.mail")
public class MailProperties {
    private boolean enabled = false;
    private String from = "知驭 ZhiYu <noreply@zhiyu.local>";
    private boolean ccPmo = true;
    private boolean async = true;

    /** 兜底邮箱(按审批 step code),生产应直接读 user.email */
    private java.util.Map<String, String> approveStepEmails = new java.util.HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public boolean isCcPmo() { return ccPmo; }
    public void setCcPmo(boolean ccPmo) { this.ccPmo = ccPmo; }
    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }
    public java.util.Map<String, String> getApproveStepEmails() { return approveStepEmails; }
    public void setApproveStepEmails(java.util.Map<String, String> approveStepEmails) {
        this.approveStepEmails = approveStepEmails;
    }
}
