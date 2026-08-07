package com.company.pmo.module.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通知统一出口(P2-A + P2-B + P2-C)。
 *
 * 关键设计:
 *  - 替代原有 NotificationListener:业务事件只走这里
 *  - 行为保持兼容:
 *      1. NotificationWriteService.writePerRecipient(写 UNREAD 一次,通道无关)—— P2-C 统一
 *      2. MailService.onXxx(发邮件,沿用旧模板)—— 完全不变
 *      3. NotificationDispatcher.dispatch(扇出到 IM 通道)—— P2-A
 *      4. SseEmitterRegistry.sendToUser(SSE 实时推送)—— P2-B
 *  - 失败兜底:异常只打 warn log,不向上抛(避免 IM/SSE 故障拖垮主业务)
 *  - @Async:不阻塞业务事务
 *
 * P2-C 改造前后对比:
 *   旧: MailService.onSubmitted() 内部调 writeUnread() + 邮件 → 1 次事件 1 条 UNREAD(写在邮件路径里)
 *   新: listener 调 writeService.writeAll() + MailService.onXxx(只发邮件) → 1 次事件 1 条 UNREAD(独立)
 *   效果: IM 通道失败 / 关闭时,UNREAD 仍能正常入库(以前邮件通道被跳过可能导致漏写)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcherListener {

    private final MailService mailService;
    private final NotificationDispatcher dispatcher;
    private final UserImBindingService bindingService;
    private final SseEmitterRegistry sseRegistry;
    private final NotificationWriteService writeService;
    private final com.company.pmo.module.org.UserRepository userRepository;

    @Async
    @EventListener
    public void onSubmitted(InitiationSubmittedEvent e) {
        try {
            // P2-C: 1) 写 UNREAD(收件人 = 部门 lead)
            Long approverId = mailService.findDeptLeadUserIdPublic(e.applicantDepartmentId());
            String title = "立项待审批: " + e.initiationCode();
            String summary = "项目【%s】已由 %s 提交,请审批".formatted(e.title(), e.applicantName());

            if (approverId != null) {
                writeService.writeOne(approverId, "INITIATION_SUBMIT",
                        e.initiationId(), e.initiationCode(), title, summary);
            }

            // 2) 发邮件(沿用旧路径 — 内部不再写 UNREAD)
            mailService.onSubmitted(e);

            // 3) IM 通道扇出
            if (approverId != null) {
                NotificationMessage msg = dispatcher.envelope(
                        "INITIATION_SUBMIT", title, summary,
                        "INITIATION", e.initiationId(), e.initiationCode(),
                        "http://localhost:8080/initiations/" + e.initiationId(),
                        List.of(approverId)
                );
                dispatcher.dispatch(msg);
                // 4) P2-B: SSE
                pushSseToUser(approverId, "INITIATION_SUBMIT", e.initiationId(),
                        e.initiationCode(), title, summary);
            }
        } catch (Exception ex) {
            log.warn("[Dispatcher] onSubmitted failed: code={} err={}", e.initiationCode(), ex.getMessage());
        }
    }

    @Async
    @EventListener
    public void onDecided(InitiationDecidedEvent e) {
        try {
            String decisionZh = switch (e.decision()) {
                case "APPROVED" -> "已批准";
                case "REJECTED" -> "已驳回";
                case "SUPPLEMENT" -> "需补料";
                default -> e.decision();
            };
            String title = "立项%s: %s".formatted(decisionZh, e.initiationCode());
            String summary = "项目【%s】%s。审批人: %s".formatted(e.title(), decisionZh, e.approverName());

            // P2-C: 1) 写 UNREAD(申请人 + 下一审批人)
            List<NotificationWriteService.OneWrite> writes = new ArrayList<>();
            if (e.applicantUserId() != null) {
                writes.add(new NotificationWriteService.OneWrite(
                        e.applicantUserId(), "INITIATION_DECIDE",
                        e.initiationId(), e.initiationCode(),
                        title, "您的项目【%s】%s。审批人: %s".formatted(e.title(), decisionZh, e.approverName())
                ));
            }
            if (e.nextStepUserId() != null) {
                writes.add(new NotificationWriteService.OneWrite(
                        e.nextStepUserId(), "INITIATION_DECIDE",
                        e.initiationId(), e.initiationCode(),
                        "待审批(下级): " + e.initiationCode(),
                        "项目【%s】已通过 %s 级审批,请处理".formatted(e.title(), e.nextStepName())
                ));
            }
            writeService.writeAll(writes);

            // 2) 发邮件
            mailService.onDecided(e);

            // 3) IM 通道扇出 + 4) SSE
            List<Long> recipients = new ArrayList<>();
            if (e.applicantUserId() != null) recipients.add(e.applicantUserId());
            if (e.nextStepUserId() != null) recipients.add(e.nextStepUserId());
            if (!recipients.isEmpty()) {
                NotificationMessage msg = dispatcher.envelope(
                        "INITIATION_DECIDE", title, summary,
                        "INITIATION", e.initiationId(), e.initiationCode(),
                        "http://localhost:8080/initiations/" + e.initiationId(),
                        recipients
                );
                dispatcher.dispatch(msg);
                for (Long uid : recipients) {
                    pushSseToUser(uid, "INITIATION_DECIDE", e.initiationId(),
                            e.initiationCode(), title, summary);
                }
            }
        } catch (Exception ex) {
            log.warn("[Dispatcher] onDecided failed: code={} err={}", e.initiationCode(), ex.getMessage());
        }
    }

    @Async
    @EventListener
    public void onResubmitted(InitiationResubmittedEvent e) {
        try {
            // P2-C: 1) 写 UNREAD(当前审批人)
            if (e.currentStepUserId() != null) {
                String title = "补料重提: " + e.initiationCode();
                String summary = "项目【%s】已由 %s 补料重提,请继续审批".formatted(e.title(), e.applicantName());
                writeService.writeOne(e.currentStepUserId(), "INITIATION_SUPPLEMENT",
                        e.initiationId(), e.initiationCode(), title, summary);

                // 2) 发邮件
                mailService.onResubmitted(e);

                // 3) IM 通道
                NotificationMessage msg = dispatcher.envelope(
                        "INITIATION_SUPPLEMENT", title, summary,
                        "INITIATION", e.initiationId(), e.initiationCode(),
                        "http://localhost:8080/initiations/" + e.initiationId(),
                        List.of(e.currentStepUserId())
                );
                dispatcher.dispatch(msg);
                // 4) SSE
                pushSseToUser(e.currentStepUserId(), "INITIATION_SUPPLEMENT", e.initiationId(),
                        e.initiationCode(), title, summary);
            }
        } catch (Exception ex) {
            log.warn("[Dispatcher] onResubmitted failed: code={} err={}", e.initiationCode(), ex.getMessage());
        }
    }

    @Async
    @EventListener
    public void onTimesheetSubmitted(TimesheetSubmittedEvent e) {
        try {
            // 1) 找所有 PMO_ADMIN/EXEC/ADMIN(可审批工时的人),排除提交人自己
            List<String> roles = List.of("PMO_ADMIN", "EXEC", "ADMIN");
            List<Long> approverIds = userRepository
                    .findAllByPrimaryRoleCodeInAndEnabledAndDeletedFalse(roles, true)
                    .stream()
                    .map(com.company.pmo.module.org.AppUser::getId)
                    .filter(id -> !id.equals(e.submitterUserId()))
                    .toList();

            String title = "工时周报待审批: " + e.resourceCode();
            String summary = "%s 提交了 %s ~ %s 的工时周报(%.1fh,%d 项目,%d 行),请审批"
                    .formatted(
                            e.submitterName() == null ? "同事" : e.submitterName(),
                            e.weekStart(), e.weekEnd(),
                            e.totalHours() == null ? 0.0 : e.totalHours(),
                            e.projectCount() == null ? 0 : e.projectCount(),
                            e.entryCount() == null ? 0 : e.entryCount()
                    );

            // P2-C: 2) 写 UNREAD(每个审批人 1 条)
            for (Long uid : approverIds) {
                writeService.writeOne(uid, "TIMESHEET_SUBMIT",
                        e.timesheetId(), e.resourceCode(), title, summary);
            }

            // 3) IM 通道扇出(每个审批人独立决定路由 — DND/IM 绑定)
            if (!approverIds.isEmpty()) {
                NotificationMessage msg = dispatcher.envelope(
                        "TIMESHEET_SUBMIT", title, summary,
                        "TIMESHEET", e.timesheetId(), e.resourceCode(),
                        "http://localhost:8080/timesheets/approvals",
                        approverIds
                );
                dispatcher.dispatch(msg);
                // 4) SSE
                for (Long uid : approverIds) {
                    pushSseToUser(uid, "TIMESHEET_SUBMIT", e.timesheetId(),
                            e.resourceCode(), title, summary);
                }
            }
        } catch (Exception ex) {
            log.warn("[Dispatcher] onTimesheetSubmitted failed: id={} err={}",
                    e.timesheetId(), ex.getMessage());
        }
    }

    @Async
    @EventListener
    public void onTimesheetDecided(TimesheetDecidedEvent e) {
        try {
            String decisionZh = switch (e.decision()) {
                case "APPROVED" -> "已批准";
                case "REJECTED" -> "已驳回";
                default -> e.decision();
            };
            String title = "工时%s: %s".formatted(decisionZh, e.resourceCode());
            String summary = e.decision().equals("REJECTED") && e.comment() != null
                    ? "你的 %s ~ %s 工时周报被驳回:%s。审批人: %s"
                            .formatted(e.weekStart(), e.weekEnd(), e.comment(), e.approverName())
                    : "你的 %s ~ %s 工时周报已%s。审批人: %s"
                            .formatted(e.weekStart(), e.weekEnd(), decisionZh, e.approverName());

            if (e.submitterUserId() == null) return;

            // P2-C: 1) 写 UNREAD(只通知提交人)
            writeService.writeOne(e.submitterUserId(), "TIMESHEET_DECIDE",
                    e.timesheetId(), e.resourceCode(), title, summary);

            // 2) IM 通道 + SSE
            NotificationMessage msg = dispatcher.envelope(
                    "TIMESHEET_DECIDE", title, summary,
                    "TIMESHEET", e.timesheetId(), e.resourceCode(),
                    "http://localhost:8080/timesheets",
                    List.of(e.submitterUserId())
            );
            dispatcher.dispatch(msg);
            pushSseToUser(e.submitterUserId(), "TIMESHEET_DECIDE", e.timesheetId(),
                    e.resourceCode(), title, summary);
        } catch (Exception ex) {
            log.warn("[Dispatcher] onTimesheetDecided failed: id={} err={}",
                    e.timesheetId(), ex.getMessage());
        }
    }

    @Async
    @EventListener
    public void onTimesheetBatchApproved(TimesheetBatchApprovedEvent e) {
        try {
            if (e.submitterUserIds() == null || e.submitterUserIds().isEmpty()) return;
            // 收件人已由 Service 端去重(LinkedHashSet),这里再防御一次
            List<Long> dedup = e.submitterUserIds().stream().distinct().toList();
            String approver = e.approverName() == null ? "审批人" : e.approverName();
            String summary = "%s 一次性批准了你 %d 份工时周报(%s~%s),共批了 %d/%d 份"
                    .formatted(approver, e.approvedCount(), e.weekStart(), e.weekEnd(),
                            e.approvedCount(), e.requestedCount());

            for (Long uid : dedup) {
                writeService.writeOne(uid, "TIMESHEET_BATCH_APPROVED",
                        e.batchId(), e.resourceCode(), e.title(), summary);
            }

            NotificationMessage msg = dispatcher.envelope(
                    "TIMESHEET_BATCH_APPROVED", e.title(), summary,
                    "TIMESHEET", e.batchId(), e.resourceCode(),
                    "http://localhost:8080/timesheets",
                    dedup
            );
            dispatcher.dispatch(msg);
            for (Long uid : dedup) {
                pushSseToUser(uid, "TIMESHEET_BATCH_APPROVED", e.batchId(),
                        e.resourceCode(), e.title(), summary);
            }
        } catch (Exception ex) {
            log.warn("[Dispatcher] onTimesheetBatchApproved failed: code={} err={}",
                    e.resourceCode(), ex.getMessage());
        }
    }

    @Async
    @EventListener
    public void onTimesheetReminder(TimesheetReminderEvent e) {
        try {
            if (e.targetUserIds() == null || e.targetUserIds().isEmpty()) return;
            List<Long> dedup = e.targetUserIds().stream().distinct().toList();
            String roundZh = switch (e.round() == null ? "" : e.round()) {
                case "WED" -> "周三预警";
                case "FRI" -> "周五强制";
                case "MON" -> "周一提醒";
                default -> e.round() == null ? "" : e.round();
            };
            String summary = "本周(%s~%s)工时周报尚未提交,请尽快补录。%s"
                    .formatted(e.weekStart(), e.weekEnd(), roundZh);
            String title = roundZh.isEmpty() ? e.title() : "[%s] %s".formatted(roundZh, e.title());

            for (Long uid : dedup) {
                writeService.writeOne(uid, "TIMESHEET_REMINDER",
                        (long) e.weekStart().hashCode(), e.resourceCode(), title, summary);
            }

            NotificationMessage msg = dispatcher.envelope(
                    "TIMESHEET_REMINDER", title, summary,
                    "TIMESHEET_REMINDER", 0L, e.resourceCode(),
                    "http://localhost:8080/timesheets",
                    dedup
            );
            dispatcher.dispatch(msg);
            for (Long uid : dedup) {
                pushSseToUser(uid, "TIMESHEET_REMINDER", 0L, e.resourceCode(), title, summary);
            }
            log.info("[Reminder] 催办 {} 发出,通知 {} 人", e.round(), dedup.size());
        } catch (Exception ex) {
            log.warn("[Dispatcher] onTimesheetReminder failed: round={} err={}",
                    e.round(), ex.getMessage());
        }
    }

    /**
     * P2-B: 推一条新通知给指定用户(通过 SSE 实时通道)。
     */
    private void pushSseToUser(Long userId, String category, Long resourceId,
                              String resourceCode, String title, String summary) {
        try {
            Map<String, Object> payload = Map.of(
                    "category", category,
                    "resourceId", resourceId,
                    "resourceCode", resourceCode,
                    "title", title,
                    "summary", summary,
                    "ts", Instant.now().toString()
            );
            sseRegistry.sendToUser(userId, payload);
            log.debug("[SSE] pushed to userId={} category={}", userId, category);
        } catch (Exception e) {
            log.debug("[SSE] push failed for userId={}: {}", userId, e.getMessage());
        }
    }

    // ============================================================
    // P5-IM 推送: 监听 MilestoneAdvisoryDecidedEvent
    //
    // 触发: MilestoneAiAdvisorService.runForMilestone() / runBatch()
    // 通道: 钉钉 / 企微 / 飞书 (由 NotificationDispatcher.envelope 内部决定)
    // 静默: UserImQuietHours 自动按用户偏好抑制 (IM 通道层)
    // 兜底: 失败仅 warn log, 不向上抛 (避免 IM 故障拖垮主业务)
    // ============================================================
    @Async
    @EventListener
    public void onMilestoneAdvisoryDecided(
            com.company.pmo.module.notification.MilestoneAdvisoryDecidedEvent e) {
        try {
            if (e.recipientUserIds() == null || e.recipientUserIds().isEmpty()) {
                log.debug("[AI Advisor] 无收件人, skip dispatch: advisoryId={}", e.advisoryId());
                return;
            }
            // INFO 级别降噪 (除非强制 override)
            if ("INFO".equals(e.severity())) {
                log.debug("[AI Advisor] INFO 级别默认不推送: advisoryId={}", e.advisoryId());
                return;
            }
            List<Long> dedup = e.recipientUserIds().stream().distinct().toList();
            String title = e.title();
            String summary = e.summary();
            String linkUrl = "http://localhost:8080/milestones/ai-advisor?projectId="
                    + e.projectId() + "&advisoryId=" + e.advisoryId();

            // P2-C: 1) 写 UNREAD (每收件人 1 条)
            for (Long uid : dedup) {
                writeService.writeOne(uid, "MILESTONE_AI_ADVISORY",
                        e.advisoryId(), e.advisoryCode(), title, summary);
            }

            // 2) IM 通道扇出 (DingTalk / WechatWork / Feishu 内部按 binding 决定)
            NotificationMessage msg = dispatcher.envelope(
                    "MILESTONE_AI_ADVISORY", title, summary,
                    "MILESTONE_AI", e.advisoryId(), e.advisoryCode(),
                    linkUrl, dedup
            );
            int sent = dispatcher.dispatch(msg);

            // 3) SSE 实时推送 (前端 NotificationCenter 弹 toast)
            for (Long uid : dedup) {
                pushSseToUser(uid, "MILESTONE_AI_ADVISORY", e.advisoryId(),
                        e.advisoryCode(), title, summary);
            }
            log.info("[AI Advisor] 已派发: advisoryId={} severity={} score={} recipients={} sent={}",
                    e.advisoryId(), e.severity(), e.score(), dedup.size(), sent);
        } catch (Exception ex) {
            log.warn("[Dispatcher] onMilestoneAdvisoryDecided failed: id={} err={}",
                    e.advisoryId(), ex.getMessage());
        }
    }
}
