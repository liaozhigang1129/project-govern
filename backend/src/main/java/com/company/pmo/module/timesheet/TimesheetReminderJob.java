package com.company.pmo.module.timesheet;

import com.company.pmo.module.notification.TimesheetReminderEvent;
import com.company.pmo.module.org.AppUser;
import com.company.pmo.module.org.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 工时催办定时任务
 *
 * <p>策略:
 * <ul>
 *   <li>周三(11:00) 预警 — 列出"本周还没提交"的人,发温和提示
 *   <li>周五(17:00) 强制 — 同样扫一次,文案更硬
 * </ul>
 * 收件人 = 启用的、未被删除的所有"应该交工时"的用户
 *     - 排除:今天休息的(简单粗暴先不过滤;V2 可接 leave 表)
 *     - 排除:已离职(deleted)
 *     - 排除:PMO_ADMIN / EXEC(他们不用录,但需要审批) — 简化为"任何非 admin/exec"且 "本周无任何状态周报"
 *
 * <p>为什么"无任何状态周报"也算催?因为有些用户根本没建草稿,这样也能提醒到
 *  - 真正"没活儿"的人会在收到催办后跳到 /timesheets 主动选"暂不提交"留痕,V2 增强
 *
 * <p>用 @Scheduled 触发,ApplicationEventPublisher 发事件(复用 P2-C 通知架构)
 *
 * <p>配置:
 * <pre>
 *   pmo.timesheet.reminder.enabled = true   (false 时整个 Job 跳过,方便压测或关闭)
 *   pmo.timesheet.reminder.wed-cron = "0 0 11 * * WED"
 *   pmo.timesheet.reminder.fri-cron = "0 0 17 * * FRI"
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TimesheetReminderJob {

    private final TimesheetWeekRepository weekRepo;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 周三 11:00 预警 */
    @Scheduled(cron = "${pmo.timesheet.reminder.wed-cron:0 0 11 * * WED}", zone = "Asia/Shanghai")
    public void runWednesday() {
        runOnce("WED");
    }

    /** 周五 17:00 强制 */
    @Scheduled(cron = "${pmo.timesheet.reminder.fri-cron:0 0 17 * * FRI}", zone = "Asia/Shanghai")
    public void runFriday() {
        runOnce("FRI");
    }

    /**
     * 跑一次催办
     * - 计算本周一(weekStart)
     * - 取所有"应该交工时"的用户(启用、未删、非 PMO/EXEC/ADMIN)
     * - 取本周已交(SUBMITTED/APPROVED)的 userId 集合
     * - 差集 = 待催
     * - 发 TimesheetReminderEvent
     */
    @Transactional(readOnly = true)
    public void runOnce(String round) {
        try {
            LocalDate weekStart = mondayOf(LocalDate.now());
            LocalDate weekEnd = weekStart.plusDays(6);

            // 1) 已交的人
            Set<Long> submittedSet = new LinkedHashSet<>(
                    weekRepo.findSubmittedUserIdsForWeek(weekStart));

            // 2) 所有"应该交工时"的人(过滤掉 PMO/EXEC/ADMIN,以及"今天应该是休息"先不过滤)
            List<Long> needSubmit = new ArrayList<>();
            List<String> needSubmitNames = new ArrayList<>();
            for (AppUser u : userRepository.findAll()) {
                if (u.isDeleted()) continue;
                if (!u.isEnabled()) continue;
                String role = u.getPrimaryRole() == null ? null : u.getPrimaryRole().getCode();
                if (role == null) continue;
                // 角色白名单:只有非 PMO/EXEC/ADMIN 才需要录工时
                if (role.equals("PMO_ADMIN") || role.equals("EXEC") || role.equals("ADMIN")) continue;
                // 3) 差集
                if (!submittedSet.contains(u.getId())) {
                    needSubmit.add(u.getId());
                    needSubmitNames.add(u.getFullName() == null ? u.getUsername() : u.getFullName());
                }
            }

            if (needSubmit.isEmpty()) {
                log.info("[Reminder] {} 本周无未交人员,跳过", round);
                return;
            }

            String resourceCode = "TS-REMINDER-%s".formatted(weekStart.toString());
            String title = "工时催办:本周周报尚未提交(%s)".formatted(weekStart);

            eventPublisher.publishEvent(new TimesheetReminderEvent(
                    title,
                    resourceCode,
                    needSubmit,
                    needSubmitNames,
                    weekStart.toString(),
                    weekEnd.toString(),
                    round,
                    needSubmit.size(),
                    Instant.now()
            ));

            log.info("[Reminder] {} 本周({})催办:{} 人(已交 {} 人,免催)",
                    round, weekStart, needSubmit.size(), submittedSet.size());
        } catch (Exception ex) {
            log.warn("[Reminder] {} 跑批失败:{}", round, ex.getMessage(), ex);
        }
    }

    private static LocalDate mondayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }
}
