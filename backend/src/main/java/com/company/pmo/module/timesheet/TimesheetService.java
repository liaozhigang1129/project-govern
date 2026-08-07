package com.company.pmo.module.timesheet;

import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.notification.TimesheetBatchApprovedEvent;
import com.company.pmo.module.notification.TimesheetDecidedEvent;
import com.company.pmo.module.notification.TimesheetSubmittedEvent;
import com.company.pmo.module.org.AppUser;
import com.company.pmo.module.org.UserRepository;
import com.company.pmo.module.timesheet.dto.TimesheetDtos.ApproveRequest;
import com.company.pmo.module.timesheet.dto.TimesheetDtos.CreateRequest;
import com.company.pmo.module.timesheet.dto.TimesheetDtos.EntriesRequest;
import com.company.pmo.module.timesheet.dto.TimesheetDtos.EntryRequest;
import com.company.pmo.module.timesheet.dto.TimesheetDtos.SubmitRequest;
import com.company.pmo.module.timesheet.dto.TimesheetResponses;
import com.company.pmo.module.timesheet.dto.TimesheetResponses.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimesheetService {

    private final TimesheetWeekRepository repo;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ============================================================
    // 1) 创建/查询
    // ============================================================

    /** 新建空白周报(若已存在返回原值 — UPSERT 语义) */
    @Transactional
    public Detail createOrGet(CreateRequest req) {
        if (req.getUserId() == null || req.getUserId() <= 0) {
            throw new BusinessException(400, "userId 必须为正整数,实得: " + req.getUserId());
        }
        validateMonday(req.getWeekStart());
        LocalDate weekEnd = req.getWeekStart().plusDays(6);
        return repo.findByUserIdAndWeekStartAndDeletedFalse(req.getUserId(), req.getWeekStart())
                .map(this::toDetailWithNames)
                .orElseGet(() -> {
                    TimesheetWeek t = new TimesheetWeek();
                    t.setUserId(req.getUserId());
                    t.setWeekStart(req.getWeekStart());
                    t.setWeekEnd(weekEnd);
                    t.setStatus(TimesheetStatus.DRAFT);
                    return toDetailWithNames(repo.save(t));
                });
    }

    @Transactional(readOnly = true)
    public Page<Summary> search(Long userId, TimesheetStatus status,
                                LocalDate from, LocalDate to, Pageable pageable) {
        Page<Summary> raw = repo.search(userId, status, from, to, pageable)
                .map(TimesheetResponses::toSummary);
        // P2.A 补:批量查 userName / approverName,避免 N+1
        Set<Long> userIds = new HashSet<>();
        raw.getContent().forEach(s -> {
            if (s.getUserId() != null) userIds.add(s.getUserId());
            if (s.getApproverId() != null) userIds.add(s.getApproverId());
        });
        if (userIds.isEmpty()) return raw;
        Map<Long, String> nameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
        raw.getContent().forEach(s -> {
            s.setUserName(nameMap.getOrDefault(s.getUserId(), ""));
            s.setApproverName(s.getApproverId() == null ? null : nameMap.getOrDefault(s.getApproverId(), ""));
        });
        return raw;
    }

    @Transactional(readOnly = true)
    public Detail getById(Long id) {
        return repo.findByIdAndDeletedFalse(id)
                .map(this::toDetailWithNames)
                .orElseThrow(() -> new BusinessException(404, "工时周报不存在: " + id));
    }

    /** 包装: 填 userName/approverName 后返回 */
    private Detail toDetailWithNames(TimesheetWeek t) {
        Detail d = TimesheetResponses.toDetail(t);
        if (t.getUserId() != null) {
            d.setUserName(userRepository.findById(t.getUserId())
                    .map(AppUser::getFullName).orElse(""));
        }
        if (t.getApproverId() != null) {
            d.setApproverName(userRepository.findById(t.getApproverId())
                    .map(AppUser::getFullName).orElse(""));
        }
        return d;
    }

    // ============================================================
    // 2) 批量 upsert 明细
    // ============================================================

    @Transactional
    public Detail upsertEntries(Long id, EntriesRequest req) {
        TimesheetWeek t = repo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "工时周报不存在: " + id));
        if (t.getStatus() != TimesheetStatus.DRAFT) {
            throw new BusinessException(409, "只能修改 DRAFT 状态,当前: " + t.getStatus());
        }
        // 14 天锁(超过 2 周前的不可补)
        LocalDate today = LocalDate.now();
        if (t.getWeekStart().isBefore(today.minusDays(14))) {
            throw new BusinessException(409, "超过 14 天的周报已锁定,不可补录");
        }
        // 校验:workDate ∈ [weekStart, weekEnd]
        Map<Long, TimesheetEntry> existing = new HashMap<>();
        t.getEntries().forEach(e -> existing.put(e.getId(), e));
        // 校验:workDate ∈ [weekStart, weekEnd] + hours 上限
        for (EntryRequest er : req.getEntries()) {
            if (er.getWorkDate().isBefore(t.getWeekStart()) || er.getWorkDate().isAfter(t.getWeekEnd())) {
                throw new BusinessException(400, "workDate " + er.getWorkDate() + " 超出本周范围 ["
                        + t.getWeekStart() + "," + t.getWeekEnd() + "]");
            }
            if (er.getHours() == null || er.getHours().compareTo(BigDecimal.ZERO) < 0
                    || er.getHours().compareTo(new BigDecimal("24")) > 0) {
                throw new BusinessException(400, "hours 必须在 [0, 24] 之间,实得 " + er.getHours());
            }
            TimesheetEntry e;
            if (er.getId() != null && existing.containsKey(er.getId())) {
                e = existing.get(er.getId());
            } else {
                e = new TimesheetEntry();
                e.setTimesheet(t);
                t.getEntries().add(e);
            }
            e.setWorkDate(er.getWorkDate());
            e.setProjectId(er.getProjectId());
            e.setMilestoneId(er.getMilestoneId());
            e.setHours(er.getHours());
            e.setDescription(er.getDescription());
        }
        return toDetailWithNames(repo.save(t));
    }

    // ============================================================
    // 3) 提交 / 审批
    // ============================================================

    @Transactional
    public Detail submit(Long id, Long userId, SubmitRequest req) {
        TimesheetWeek t = mustOwn(id, userId);
        if (t.getStatus() == TimesheetStatus.APPROVED) {
            throw new BusinessException(409, "已审批,不能重复提交");
        }
        if (t.getStatus() != TimesheetStatus.DRAFT) {
            throw new BusinessException(409, "只有 DRAFT 可提交,当前: " + t.getStatus());
        }
        if (t.getEntries().isEmpty()) {
            throw new BusinessException(400, "周报无明细,不能提交");
        }
        t.setStatus(TimesheetStatus.SUBMITTED);
        t.setSubmittedAt(Instant.now());
        t.setSubmitterNote(req.getSubmitterNote());

        // P3: 事务提交后发事件(防止主事务回滚导致虚假通知)
        String title = "工时周报待审批: " + buildResourceCode(t.getId(), t.getUserId(), t.getWeekStart());
        publishAfterCommit(new TimesheetSubmittedEvent(
                t.getId(),
                title,
                buildResourceCode(t.getId(), t.getUserId(), t.getWeekStart()),
                t.getUserId(),
                userRepository.findById(t.getUserId()).map(AppUser::getFullName).orElse(""),
                t.getWeekStart().toString(),
                t.getWeekEnd().toString(),
                sumHours(t).doubleValue(),
                countProjects(t),
                t.getEntries().size(),
                Instant.now()
        ));
        return toDetailWithNames(t);
    }

    @Transactional
    public Detail approve(Long id, Long approverId) {
        TimesheetWeek t = repo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "工时周报不存在: " + id));
        if (t.getStatus() != TimesheetStatus.SUBMITTED) {
            throw new BusinessException(409, "只有 SUBMITTED 可审批,当前: " + t.getStatus());
        }
        t.setStatus(TimesheetStatus.APPROVED);
        t.setApproverId(approverId);
        t.setApprovedAt(Instant.now());

        // 标记:批量审批过程中,单条事件也要发(让"单条批准"路径的通知流不变)
        // 批量事件会在 batchApprove() 末尾再补一个 —— 因此收件人最终会看到
        //   [单条] + [批量汇总] 两条
        // 但为了避免双发,这里加个判断:如果当前是批量流程中(由 batchApprove 内部调用),
        //   就只发"批量",不这里发
        // —— 实现:用 ThreadLocal 标记
        boolean skipSingleEvent = BatchApproveContext.isInBatch();
        if (!skipSingleEvent) {
            publishAfterCommit(new TimesheetDecidedEvent(
                    t.getId(),
                    "工时已批准: " + buildResourceCode(t.getId(), t.getUserId(), t.getWeekStart()),
                    buildResourceCode(t.getId(), t.getUserId(), t.getWeekStart()),
                    t.getUserId(),
                    userRepository.findById(t.getUserId()).map(AppUser::getFullName).orElse(""),
                    approverId,
                    userRepository.findById(approverId).map(AppUser::getFullName).orElse(""),
                    "APPROVED",
                    null,
                    t.getWeekStart().toString(),
                    t.getWeekEnd().toString(),
                    Instant.now()
            ));
        }
        return toDetailWithNames(t);
    }

    /** P3 驳回(SUBMITTED → DRAFT,留 comment 给提交人看) */
    @Transactional
    public Detail reject(Long id, Long approverId, ApproveRequest req) {
        TimesheetWeek t = repo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "工时周报不存在: " + id));
        if (t.getStatus() != TimesheetStatus.SUBMITTED) {
            throw new BusinessException(409, "只有 SUBMITTED 可驳回,当前: " + t.getStatus());
        }
        if (req == null || req.getComment() == null || req.getComment().trim().length() < 5) {
            throw new BusinessException(400, "驳回必须填写理由(至少 5 个字符)");
        }
        // 驳回后回 DRAFT:批准人清空,留 submitterNote 给提交人
        String reason = req.getComment().trim();
        t.setStatus(TimesheetStatus.DRAFT);
        t.setApproverId(approverId);                  // 记录谁驳回的,留痕
        t.setApprovedAt(null);
        t.setSubmitterNote("【驳回】" + reason);

        publishAfterCommit(new TimesheetDecidedEvent(
                t.getId(),
                "工时已驳回: " + buildResourceCode(t.getId(), t.getUserId(), t.getWeekStart()),
                buildResourceCode(t.getId(), t.getUserId(), t.getWeekStart()),
                t.getUserId(),
                userRepository.findById(t.getUserId()).map(AppUser::getFullName).orElse(""),
                approverId,
                userRepository.findById(approverId).map(AppUser::getFullName).orElse(""),
                "REJECTED",
                reason,
                t.getWeekStart().toString(),
                t.getWeekEnd().toString(),
                Instant.now()
        ));
        return toDetailWithNames(t);
    }

    // ============================================================
    // 4.5) 批量审批(1 次发事件 + 收件人去重)
    // ============================================================

    /**
     * P3-V2 批量批准
     *  - 每条单独事务(单条失败不阻塞其他)
     *  - 整体只发 1 个 TimesheetBatchApprovedEvent,每个提交人 1 条 UNREAD + 1 次 IM
     *  - 收件人:被批周报的提交人(去重)
     *  - 操作人(approverId)不再单独发通知 —— 已通过 UNREAD category 的 title 包含审批人姓名交代
     */
    @Transactional
    public BatchApproveResult batchApprove(List<Long> ids, Long approverId) {
        if (ids == null || ids.isEmpty()) {
            return new BatchApproveResult(List.of(), 0, 0);
        }
        // 用 LinkedHashSet 保序去重(防止同一人提交了多份时被骚扰)
        Set<Long> dedupIds = new LinkedHashSet<>(ids);

        List<Detail> approved = new java.util.ArrayList<>();
        Set<Long> dedupSubmitters = new LinkedHashSet<>();
        List<Long> approvedTimesheetIds = new java.util.ArrayList<>();
        String sharedWeekStart = null;
        String sharedWeekEnd = null;
        int requested = dedupIds.size();

        // P3-V2 关键:用 ThreadLocal 标记"在批量流程中",让 approve() 跳过单条事件,避免双发
        BatchApproveContext.enter();
        try {
            for (Long id : dedupIds) {
                try {
                    Detail d = approve(id, approverId);
                    approved.add(d);
                    approvedTimesheetIds.add(d.getId());
                    if (d.getUserId() != null) dedupSubmitters.add(d.getUserId());
                    if (sharedWeekStart == null && d.getWeekStart() != null) {
                        sharedWeekStart = d.getWeekStart().toString();
                        sharedWeekEnd = d.getWeekEnd().toString();
                    }
                } catch (BusinessException e) {
                    // 单条失败跳过,继续下一条
                }
            }
        } finally {
            BatchApproveContext.exit();
        }

        if (approved.isEmpty()) {
            return new BatchApproveResult(approved, requested, 0);
        }

        // 发"批量"事件(用 category=BATCH_APPROVED,收件人=所有去重后的提交人)
        // 不再发 N 个单条 DecidedEvent —— 已通过上面的 approve() 发过 1 次/条
        String approverName = userRepository.findById(approverId)
                .map(AppUser::getFullName).orElse("");
        String batchResourceCode = "TS-BATCH-%d-%s".formatted(
                approved.size(),
                sharedWeekStart == null ? "multi" : sharedWeekStart);
        String batchTitle = "工时批量已批准(%d 份): %s".formatted(approved.size(), batchResourceCode);
        String summary = "%s 一次性批准了你 %d 份工时周报(%s~%s),共 %.1fh"
                .formatted(approverName, approved.size(),
                        sharedWeekStart, sharedWeekEnd,
                        approved.stream().map(Detail::getTotalHours)
                                .filter(java.util.Objects::nonNull)
                                .mapToDouble(BigDecimal::doubleValue).sum());

        publishAfterCommit(new TimesheetBatchApprovedEvent(
                approved.size() == 0 ? 0L : (long) approved.get(0).getId(),
                batchTitle,
                batchResourceCode,
                new java.util.ArrayList<>(dedupSubmitters),
                approvedTimesheetIds,
                String.valueOf(approverId),
                approverName,
                sharedWeekStart == null ? "" : sharedWeekStart,
                sharedWeekEnd == null ? "" : sharedWeekEnd,
                approved.size(),
                requested,
                Instant.now()
        ));
        return new BatchApproveResult(approved, requested, approved.size());
    }

    /** 批量审批结果(供 Controller/前端) */
    public record BatchApproveResult(List<Detail> approved, int requested, int successCount) {}

    // ============================================================
    // 5) 软删
    // ============================================================

    @Transactional
    public void softDelete(Long id, Long userId) {
        TimesheetWeek t = mustOwn(id, userId);
        if (t.getStatus() == TimesheetStatus.SUBMITTED || t.getStatus() == TimesheetStatus.APPROVED) {
            throw new BusinessException(409, "已提交/审批的周报不能删除");
        }
        t.setDeleted(true);
    }

    // ============================================================
    // 工具
    // ============================================================

    private TimesheetWeek mustOwn(Long id, Long userId) {
        TimesheetWeek t = repo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "工时周报不存在: " + id));
        if (!t.getUserId().equals(userId)) {
            throw new BusinessException(403, "非本人周报,无权操作");
        }
        return t;
    }

    /** 静态工具:d 所在周的周一 */
    public static LocalDate mondayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    private void validateMonday(LocalDate d) {
        if (!d.getDayOfWeek().toString().equals("MONDAY")) {
            throw new BusinessException(400, "weekStart 必须是周一,实得 " + d.getDayOfWeek());
        }
    }

    // ============================================================
    // P3: 通知事件 — 工具方法
    // ============================================================

    /**
     * 事务提交后才发 Spring 事件(AFTER_COMMIT)。
     * - 目的:主业务事务回滚时,不要让审批人收到"已提交"的虚假通知
     * - 如果当前不在事务中(理论不会,submit/approve/reject 都被 @Transactional 包了),
     *   退化成立即发,保持兼容性
     */
    private void publishAfterCommit(Object event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    eventPublisher.publishEvent(event);
                }
            });
        } else {
            eventPublisher.publishEvent(event);
        }
    }

    private String buildResourceCode(Long timesheetId, Long userId, LocalDate weekStart) {
        return "TS-%s-u%d-%d".formatted(weekStart.toString(), userId, timesheetId);
    }

    private BigDecimal sumHours(TimesheetWeek t) {
        return t.getEntries().stream()
                .map(e -> e.getHours() == null ? BigDecimal.ZERO : e.getHours())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private int countProjects(TimesheetWeek t) {
        return (int) t.getEntries().stream()
                .map(e -> e.getProjectId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
    }
}
