package com.hex.projectgovern.module.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * 钉钉请休假同步服务 (P5)
 *
 * 增量同步策略:
 *   1) 读取 dingtalk_leave_sync_state.last_sync_time 作为起点
 *   2) 调用 listLeaves(start_time, end_time=now)
 *   3) 按 leave_id 去重 -> 新增/更新
 *   4) 失效检测: 本地已存在但拉取列表中无 -> 标记 deleted
 *   5) 更新 sync_state
 *
 * 调用入口: syncNow(triggerType, triggeredBy) - 异步执行, 立即返回 RUNNING 日志
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DingTalkLeaveSyncService {

    private static final String SYNC_KEY = "global";

    private final DingTalkApiClient api;
    private final DingTalkProperties props;
    private final DingTalkLeaveRepository leaveRepo;
    private final DingTalkLeaveSyncStateRepository stateRepo;
    private final DingTalkLeaveSyncLogRepository logRepo;
    private final DingTalkUserLookupRepository userLookup;

    // ============================================================
    // 同步入口 (异步)
    // ============================================================
    public DingTalkLeaveSyncLog syncNow(String triggerType, String triggeredBy, boolean fullSync) {
        // 1) 立即创建 RUNNING log
        DingTalkLeaveSyncLog slog = new DingTalkLeaveSyncLog();
        slog.setStartedAt(Instant.now());
        slog.setTriggerType(triggerType);
        slog.setTriggeredBy(triggeredBy);
        slog.setStatus("RUNNING");
        slog.setSyncMode(fullSync ? "FULL" : "INCREMENTAL");

        Instant startTime;
        if (fullSync) {
            // 全量同步: 受钉钉 QPS 限制(单 app ~20 req/s), 默认只拉最近 30 天
            // 真全量 6 个月得 ~30 分钟, 不适合手动触发; 走调度任务 nightly 跑
            startTime = Instant.now().minusSeconds(30L * 86400);
        } else {
            startTime = stateRepo.findBySyncKey(SYNC_KEY)
                    .map(DingTalkLeaveSyncState::getLastSyncTime)
                    .orElse(Instant.now().minusSeconds(30L * 86400));
        }
        slog.setLastSyncTime(startTime);

        final DingTalkLeaveSyncLog running = logRepo.save(slog);
        log.info("[DingTalkLeaveSync] 触发异步同步, logId={} mode={} trigger={} by={}",
                running.getId(), running.getSyncMode(), triggerType, triggeredBy);

        // 2) 启动后台线程
        Thread async = new Thread(() -> {
            try {
                runSync(running.getId(), fullSync);
            } catch (Exception ex) {
                log.error("[DingTalkLeaveSync] 后台同步线程异常", ex);
            }
        }, "dingtalk-leave-sync-" + running.getId());
        async.setDaemon(true);
        async.start();

        return running;
    }

    private void runSync(Long logId, boolean fullSync) {
        DingTalkLeaveSyncLog slog = logRepo.findById(logId).orElseThrow();
        Instant startTime = slog.getLastSyncTime();
        Instant endTime = Instant.now();

        try {
            // 1) 拉取
            List<JsonNode> items = api.listLeaves(
                    props.getLeaveProcessCodes(),
                    startTime.toEpochMilli(),
                    endTime.toEpochMilli(),
                    props.getLeaveWindowDays());
            slog.setFetched(items.size());

            // 2) 处理每条记录
            int created = 0, updated = 0, deleted = 0, skipped = 0;
            for (JsonNode n : items) {
                if (n == null) { skipped++; continue; }
                String leaveId = n.path("leave_id").asText("");
                if (leaveId.isEmpty()) { skipped++; continue; }
                Optional<DingTalkLeave> existingOpt = leaveRepo.findByLeaveIdAndDeletedFalse(leaveId);
                boolean isNew = existingOpt.isEmpty();
                DingTalkLeave row = isNew ? new DingTalkLeave() : existingOpt.get();

                row.setLeaveId(leaveId);
                row.setUserid(n.path("userid").asText(""));
                row.setLeaveType(n.path("leave_type").asText(""));
                Instant st = parseInstant(n.path("start_time"));
                if (st == null) {
                    log.warn("[DingTalkLeaveSync] 跳过 leaveId={} 缺 start_time", leaveId);
                    skipped++;
                    continue;
                }
                row.setStartTime(st);
                row.setEndTime(parseInstant(n.path("end_time")));    // 允许为 null
                BigDecimal dur = n.path("duration").decimalValue();
                row.setDuration(dur == null ? BigDecimal.ZERO : dur);
                row.setDurationUnit(n.path("duration_unit").asText("HOUR"));
                row.setReason(n.path("reason").asText(""));
                row.setStatus(n.path("status").asText("NORMAL"));
                row.setApproverUserid(n.path("approver_userid").asText(""));
                row.setDingtalkUpdatedAt(parseInstant(n.path("update_time")));
                row.setSyncedAt(Instant.now());
                row.setUpdatedAt(Instant.now());
                if (isNew) {
                    row.setCreatedAt(Instant.now());
                }

                // 关联 PMO 业务字段
                if (row.getUserid() != null && !row.getUserid().isEmpty()) {
                    userLookup.findByDingtalkUserId(row.getUserid()).ifPresent(u -> {
                        row.setPmoUserId(u.getId());
                        row.setUserName(u.getFullName());
                        row.setDepartmentId(u.getDepartmentId());
                    });
                }

                leaveRepo.save(row);
                if (isNew) created++; else updated++;
            }

            // 3) 失效检测 (增量同步时): 期间被撤回/删除的请休假
            if (!fullSync) {
                deleted = markDeletedIfMissing(items, startTime);
            }

            // 4) 更新状态
            slog.setCreatedCount(created);
            slog.setUpdatedCount(updated);
            slog.setDeletedCount(deleted);
            slog.setSkippedCount(skipped);
            // 4.1) 暴露"被跳过"的根因: 如果 created=updated=0 且 skipped>0, 把 API 错误信息写进 errorMessage
            //      让 admin 不用看后端日志就能秒懂"是不是权限问题"等
            if (created == 0 && updated == 0 && skipped > 0) {
                String apiErr = api.consumeLastDetailError();
                if (apiErr != null) {
                    slog.setErrorMessage("全部 " + skipped + " 条因详情 API 失败被跳过: " + apiErr);
                    slog.setErrorDetail("详情 API 错误, 可申请权限或检查 scope.\n" + apiErr);
                } else if (slog.getErrorMessage() == null) {
                    slog.setErrorMessage("全部 " + skipped + " 条被跳过, 无 created/updated, 请查看后端日志");
                }
            }
            slog.setStatus("SUCCESS");
            slog.setFinishedAt(Instant.now());
            logRepo.save(slog);

            DingTalkLeaveSyncState state = stateRepo.findBySyncKey(SYNC_KEY)
                    .orElseGet(() -> {
                        DingTalkLeaveSyncState s = new DingTalkLeaveSyncState();
                        s.setSyncKey(SYNC_KEY);
                        return s;
                    });
            state.setLastSyncTime(endTime);
            state.setLastTotal(items.size());
            state.setLastCreated(created);
            state.setLastUpdated(updated);
            state.setLastDeleted(deleted);
            state.setUpdatedAt(Instant.now());
            stateRepo.save(state);

            log.info("[DingTalkLeaveSync] logId={} 成功: fetched={} created={} updated={} deleted={} skipped={}",
                    logId, items.size(), created, updated, deleted, skipped);
        } catch (Exception e) {
            log.error("[DingTalkLeaveSync] logId={} 失败: {}", logId, e.getMessage(), e);
            slog.setStatus("FAILED");
            slog.setFinishedAt(Instant.now());
            slog.setErrorMessage(e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            slog.setErrorDetail(sw.toString());
            logRepo.save(slog);
        }
    }

    /**
     * 失效检测: 在本次同步窗口内活跃的本地记录, 但拉取列表中已不存在 -> 标记 deleted
     */
    private int markDeletedIfMissing(List<JsonNode> currentList, Instant start) {
        Set<String> liveIds = new HashSet<>();
        for (JsonNode n : currentList) {
            String id = n.path("leave_id").asText("");
            if (!id.isEmpty()) liveIds.add(id);
        }
        // 查本地近期的非 deleted 记录
        List<DingTalkLeave> local = leaveRepo.findAllActive(PageRequest.of(0, 1000,
                Sort.by(Sort.Direction.DESC, "startTime"))).getContent();
        int deleted = 0;
        for (DingTalkLeave l : local) {
            if (l.getSyncedAt() != null && l.getSyncedAt().isBefore(start)) continue;
            if (!liveIds.contains(l.getLeaveId())) {
                l.setDeleted(true);
                l.setUpdatedAt(Instant.now());
                leaveRepo.save(l);
                deleted++;
            }
        }
        return deleted;
    }

    private Instant parseInstant(JsonNode n) {
        long ms = n.asLong(0);
        if (ms == 0) return null;
        return Instant.ofEpochMilli(ms);
    }

    // ============================================================
    // 查询接口 (供前端)
    // ============================================================
    @Transactional(readOnly = true)
    public Page<DingTalkLeave> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        return leaveRepo.findAllActive(pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        long total = leaveRepo.countActive();
        long now = Instant.now().toEpochMilli();
        long monthStart = java.time.LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long monthCount = leaveRepo.countByRange(Instant.ofEpochMilli(monthStart), Instant.ofEpochMilli(now));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("thisMonth", monthCount);
        return m;
    }

    @Transactional(readOnly = true)
    public DingTalkLeaveSyncState getState() {
        return stateRepo.findBySyncKey(SYNC_KEY).orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<DingTalkLeaveSyncLog> logs(int page, int size) {
        return logRepo.findAllByOrderByStartedAtDesc(PageRequest.of(page, size));
    }
}
