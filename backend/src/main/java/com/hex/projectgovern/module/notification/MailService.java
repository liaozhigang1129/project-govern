package com.hex.projectgovern.module.notification;

import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 邮件发送服务(P2-A + P2-C)。
 *
 * 设计:
 *  - JavaMailSender 通过 ObjectProvider 注入(可能是 null,当 pmo.mail.enabled=false 时)
 *  - 模板用纯文本 SimpleMailMessage(避免引入 Thymeleaf/Freemarker)
 *  - 失败不抛(只打 warn log),业务调用方不会被邮件异常拖垮
 *  - 作为 NotificationChannel 的 EMAIL 实现: NotificationDispatcher 在路由"email"时调 send()
 *
 * P2-C 重构:
 *  - 旧 onSubmitted/onDecided/onResubmitted 里**不再写 UNREAD**(那是 NotificationWriteService 的活)
 *  - 这里只发邮件;UNREAD 统一由 NotificationDispatcherListener 写一次
 *  - 行为完全等价:触发一次业务事件 → 1 条 UNREAD + 1 封邮件(以前是同次调内一并完成,现在拆为两次但仍只 1 次入库)
 */
@Service
@Slf4j
public class MailService implements NotificationChannel {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final ApplicationContext ctx;
    /** 暴露给测试用(@VisibleForTesting);包内可见,生产不要直接改 */
    final MailProperties props;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       ApplicationContext ctx, MailProperties props) {
        this.mailSenderProvider = mailSenderProvider;
        this.ctx = ctx;
        this.props = props;
    }

    /** P2-C: 暴露给 NotificationDispatcherListener 的 dept lead 查找(替换原内联调用) */
    public Long findDeptLeadUserIdPublic(Long deptId) { return findDeptLeadUserId(deptId); }

    /** P2-C: 仅供包内(MailService + 测试)使用 — 旧版 onXxx 仍保留以兼容外部直接调用方 */
    Long findDeptLeadUserId(Long deptId) {
        return userRepo().findFirstByDepartmentIdAndPrimaryRoleCodeAndDeletedFalse(deptId, "DEPT_LEAD")
                .map(AppUser::getId).orElse(null);
    }
    private Optional<AppUser> lookupUser(Long id) { return userRepo().findById(id); }

    // ====== NotificationChannel 接口实现(P2-A) ======

    @Override public Type type() { return Type.EMAIL; }

    @Override public boolean isEnabled() { return props.isEnabled(); }

    @Override
    public boolean send(NotificationMessage msg) {
        // 1) 查收件人邮箱
        List<String> to = new ArrayList<>();
        if (msg.recipientUserIds() != null) {
            for (Long uid : msg.recipientUserIds()) {
                lookupUser(uid).map(AppUser::getEmail)
                        .filter(e -> e != null && !e.isBlank())
                        .ifPresent(to::add);
            }
        }
        if (to.isEmpty()) {
            log.debug("[Mail] no recipient emails for users={}, skip", msg.recipientUserIds());
            return false;
        }
        // 2) 渲染 subject/body
        String subject = msg.title();
        String body = buildEmailBody(msg);
        // 3) 走核心 send
        return doSend(to, List.of(), subject, body);
    }

    /** 邮件正文渲染(通用模板;V2 切换后可被所有事件复用) */
    private String buildEmailBody(NotificationMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append(msg.summary()).append("\n\n");
        if (msg.resourceCode() != null) {
            sb.append("编号: ").append(msg.resourceCode()).append("\n");
        }
        if (msg.linkUrl() != null) {
            sb.append("\n查看详情: ").append(msg.linkUrl());
        }
        sb.append("\n\n— project-govern 自动通知");
        return sb.toString();
    }

    // ====== 底层发送逻辑(原 send(to,cc,subj,body) 内联到这里) ======

    /** 核心 send(供接口 send() 与旧 send(to,cc,subj,body) 共用)。失败返回 false。 */
    private boolean doSend(List<String> to, List<String> cc, String subject, String body) {
        if (!props.isEnabled()) {
            log.debug("[Mail] disabled, skip to={} subject={}", to, subject);
            return false;
        }
        JavaMailSender sender = (mailSenderProvider == null) ? null : mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("[Mail] JavaMailSender bean missing, skip to={} subject={}", to, subject);
            return false;
        }
        if (to == null || to.isEmpty()) {
            log.warn("[Mail] no recipient, skip subject={}", subject);
            return false;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(props.getFrom());
            m.setTo(to.toArray(new String[0]));
            if (cc != null && !cc.isEmpty()) m.setCc(cc.toArray(new String[0]));
            m.setSubject(subject);
            m.setText(body);
            sender.send(m);
            log.info("[Mail] sent: to={} cc={} subject={}", to, cc, subject);
            return true;
        } catch (MailException e) {
            log.warn("[Mail] send failed: subject={} err={}", subject, e.getMessage());
            return false;
        }
    }

    /** 旧 API: 直接给 to/cc 发送(保留兼容老调用方,签名不变) */
    public void send(List<String> to, List<String> cc, String subject, String body) {
        doSend(to, cc, subject, body);
    }

    /** 旧 API: 便捷单收件人 */
    public void sendTo(String to, String subject, String body) {
        send(to == null ? List.of() : List.of(to), null, subject, body);
    }

    // ====== 模板方法(供 NotificationListener 调用) ======

    /** 懒查 repository(绕开循环依赖) */
    private NotificationRepository notifRepo() { return ctx.getBean(NotificationRepository.class); }
    private UserRepository userRepo()           { return ctx.getBean(UserRepository.class); }

    /**
     * P2-C: 旧 onXxx 不再写 UNREAD — 移到 NotificationWriteService + NotificationDispatcherListener
     * (保留 writeUnread() 方法仅供旧测试或包内老路径使用,业务事件路径不再调它)
     */
    @Deprecated
    void writeUnread(Long recipientId, String category, Long resourceId,
                     String resourceCode, String title, String content) {
        if (recipientId == null) {
            log.info("[Notif] skip write: recipientId is null, category={}, resource={}", category, resourceCode);
            return;
        }
        try {
            notifRepo().save(Notification.builder()
                    .recipientId(recipientId).category(category)
                    .resourceId(resourceId).resourceCode(resourceCode)
                    .title(title).content(content)
                    .status(Notification.NotificationStatus.UNREAD)
                    .build());
            log.info("[Notif] wrote UNREAD to userId={}, category={}, resource={}", recipientId, category, resourceCode);
        } catch (Exception ex) {
            log.warn("[Notif] write failed (non-fatal): recipientId={}, category={}, err={}", recipientId, category, ex.getMessage());
        }
    }

    public void onSubmitted(InitiationSubmittedEvent e) {
        log.info("[Notif] onSubmitted: initId={}, code={}, deptId={}", e.initiationId(), e.initiationCode(), e.applicantDepartmentId());
        // P2-C: 写 UNREAD 已由 NotificationDispatcherListener 统一处理,这里只发邮件
        // (保留 onSubmitted 签名以兼容外部测试 / 老调用方)

        // 发邮件
        String approverEmail = props.getApproveStepEmails().getOrDefault("DEPT_LEAD", "lead_wu@company.com");
        String approverName = "部门负责人";
        String subject = "【立项审批】%s 提交了 %s".formatted(e.applicantName(), e.initiationCode());
        String body = """
                %s 您好,

                收到新的立项申请,请审批:

                  编号: %s
                  标题: %s
                  申请人: %s <%s>
                  提交时间: %s

                请登录系统查看详情并审批:http://localhost:8080/initiations/%d

                — project-govern 自动通知
                """.formatted(approverName, e.initiationCode(), e.title(),
                e.applicantName(), e.applicantEmail(), e.occurredAt(), e.initiationId());
        List<String> cc = props.isCcPmo() ? List.of("admin@company.com") : List.of();
        send(List.of(approverEmail), cc, subject, body);
    }

    public void onDecided(InitiationDecidedEvent e) {
        String decisionZh = switch (e.decision()) {
            case "APPROVED" -> "已批准";
            case "REJECTED" -> "已驳回";
            case "SUPPLEMENT" -> "需补料";
            default -> e.decision();
        };

        // P2-C: 写 UNREAD 由 NotificationDispatcherListener 统一处理 — 这里不再写

        // 发邮件
        String subject = "【立项审批】%s %s — %s".formatted(e.initiationCode(), decisionZh, e.title());
        String nextLine = (e.nextStepCode() == null) ? "" :
                "\n下一步审批人: %s(%s)".formatted(e.nextStepName(), e.nextStepCode());
        String body = """
                您好,

                立项审批已处理:

                  编号: %s
                  标题: %s
                  决定: %s(%s)
                  审批人: %s
                  意见: %s
                  时间: %s%s

                登录查看详情:http://localhost:8080/initiations/%d

                — project-govern 自动通知
                """.formatted(e.initiationCode(), e.title(),
                decisionZh, e.decision(), e.approverName(),
                e.comment() == null ? "(无)" : e.comment(),
                e.occurredAt(), nextLine, e.initiationId());

        List<String> to = new ArrayList<>();
        if (e.applicantEmail() != null) to.add(e.applicantEmail());

        // 中间级(非终态、非 SUPPLEMENT):也通知下一审批人
        List<String> cc = new ArrayList<>();
        if (e.nextStepCode() != null) {
            String nextEmail = props.getApproveStepEmails().get(e.nextStepCode());
            if (nextEmail != null) cc.add(nextEmail);
        } else if (props.isCcPmo()) {
            cc.add("admin@company.com");
        }
        if (to.isEmpty() && cc.isEmpty()) {
            log.warn("[Mail] onDecided skip: applicantEmail & cc both empty, code={}", e.initiationCode());
            return;
        }
        send(to, cc, subject, body);
    }

    public void onResubmitted(InitiationResubmittedEvent e) {
        // P2-C: 写 UNREAD 由 NotificationDispatcherListener 统一处理 — 这里不再写

        // 发邮件
        String approverEmail = props.getApproveStepEmails().getOrDefault(e.currentStepCode(), "");
        String subject = "【立项补料】%s 已重新提交,请继续审批".formatted(e.initiationCode());
        String body = """
                %s 您好,

                申请人已补料并重新提交:

                  编号: %s
                  标题: %s
                  申请人: %s
                  补料时间: %s

                请登录查看并继续审批:http://localhost:8080/initiations/%d

                — project-govern 自动通知
                """.formatted(e.currentStepName(), e.initiationCode(), e.title(),
                e.applicantName(), e.occurredAt(), e.initiationId());
        List<String> cc = props.isCcPmo() ? List.of("admin@company.com") : List.of();
        send(List.of(approverEmail), cc, subject, body);
    }

    /**
     * 测试用: 用自定义 props 构造(传 null provider)
     */
    public static MailService forTest() {
        return new MailService(null, new org.springframework.context.support.StaticApplicationContext(), new MailProperties());
    }

    /**
     * 测试用: 用自定义 props 构造(传 null provider)
     */
    public static MailService forTest(MailProperties props) {
        return new MailService(null, new org.springframework.context.support.StaticApplicationContext(), props);
    }
}
