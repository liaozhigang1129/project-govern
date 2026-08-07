package com.company.pmo.module.dingtalk;

import com.company.pmo.module.notification.DingTalkChannel;
import com.company.pmo.module.notification.MailService;
import com.company.pmo.module.org.AppUser;
import com.company.pmo.module.project.Project;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 钉钉考勤同步服务 (V4.30 + V4.33)
 *
 * 同步策略 (V4.33 改造):
 *   - 拉取 PMO 系统中已绑定钉钉的全部用户, 按时间范围拉考勤
 *   - 增量:  start = last_sync_time
 *   - 全量:  start = now - N 天 (N 由 system_config 控)
 *   - 手动同步: 界面选择时间范围 → 强制 FULL 模式
 *
 * V4.33 改造核心:
 *   - runSync 改为"按 (userid, work_date) 一天一行"聚合写入 dingtalk_attendance_daily
 *   - 老表 dingtalk_attendance 冻结只读, 不再 INSERT/UPDATE
 *     (保留仅给详情抽屉 GET /raw?ids=... 反查原始打卡)
 *   - 新表 dingtalk_attendance_daily 严格 (userid, work_date) 唯一
 *   - 单行事务: @Transactional(REQUIRES_NEW) 在 upsertOne 上
 *   - 关联: pmo_user_id (AppUser), project_ids/names (timesheet_entry JOIN Project)
 *   - 失效检测: markDeletedIfMissing 改用新表 dailyRepo
 *
 * ⚠️ 不要在 runSync 路径调 attendanceRepo.save() / delete() (老表冻结)
 * ⚠️ 不要删 attendanceRepo 字段 (Controller /raw 还要用)
 *
 * 定时任务: 每周日 03:00 跑最近 2 周 (默认)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DingTalkAttendanceSyncService {

    private static final String SYNC_KEY = "global";

    private final DingTalkApiClient api;
    private final DingTalkProperties props;
    private final DingTalkAttendanceRepository attendanceRepo;
    private final DingTalkAttendanceDailyRepository dailyRepo;
    private final DingTalkAttendanceSyncStateRepository stateRepo;
    private final MailService mailService;          // V4.34 4.1
    private final DingTalkChannel dingTalkChannel;  // V4.34 4.1
    private final DingTalkAttendanceSyncLogRepository logRepo;
    private final DingTalkUserLookupRepository userLookup;
    private final com.company.pmo.module.project.ProjectRepository projectRepo;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final DingTalkAttendanceDailyUpserter dailyUpserter;  // V4.34: 独立 Bean 解决 self-invocation

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 钉钉 listRecord timeResult 取值 (V4.33 改造, 基于 DB 实测)
     * 注意: 老 service 注释的 Tardy/SeriousTardy 是错的, 实测是 Late/SeriousLate
     */
    private static final Set<String> ABNORMAL_RESULTS = Set.of(
            "Late", "Early", "SeriousLate", "SeriousEarly");

    /**
     * 兼容 check_type 新老命名
     *   老钉钉: OnDuty / OffDuty
     *   新钉钉: Before / After
     */
    private static final Set<String> ON_DUTY_TYPES = Set.of("OnDuty", "Before");
    private static final Set<String> OFF_DUTY_TYPES = Set.of("OffDuty", "After");

    /**
     * V4.33 内部 POJO: 钉钉 listRecord 原始一条记录解析后的中间结构
     * 用于 runSync 第一遍扫描后入 Map<userid, Map<workDate, List<RawRecord>>>
     * 然后第二遍按 workDate 聚合写新表 dingtalk_attendance_daily
     */
    @Getter
    private static class RawRecord {
        final String bizId;              // 钉钉主键 (listRecord 字段名)
        final String userid;             // 钉钉 userid
        final LocalDate workDate;        // 工作日
        final String checkType;          // OnDuty / OffDuty / Before / After
        final String source;             // MAP / ATM / WIFI / OTHER
        final String timeResult;         // Normal / Late / Early / SeriousLate / NotSigned
        final String locationMethod;
        final String locationResult;
        final Instant planTime;          // planCheckTime
        final Instant actualTime;        // userCheckTime
        final Instant baseCheckTime;
        final Instant dingtalkUpdatedAt; // updateTime

        RawRecord(String bizId, String userid, LocalDate workDate,
                  String checkType, String source, String timeResult,
                  String locationMethod, String locationResult,
                  Instant planTime, Instant actualTime, Instant baseCheckTime,
                  Instant dingtalkUpdatedAt) {
            this.bizId = bizId;
            this.userid = userid;
            this.workDate = workDate;
            this.checkType = checkType;
            this.source = source;
            this.timeResult = timeResult;
            this.locationMethod = locationMethod;
            this.locationResult = locationResult;
            this.planTime = planTime;
            this.actualTime = actualTime;
            this.baseCheckTime = baseCheckTime;
            this.dingtalkUpdatedAt = dingtalkUpdatedAt;
        }
    }

    // ============================================================
    // 同步入口 (异步)
    // @param from 含: 拉取区间起点; null=按增量
    // @param to   含: 拉取区间终点; null=now
    // ============================================================
    public DingTalkAttendanceSyncLog syncNow(String triggerType, String triggeredBy,
                                             LocalDate from, LocalDate to) {
        // V4.35: stale guard - 启动前清理僵尸 RUNNING
        // 触发场景: 上一次进程被 kill / OOM / 手动 ctrl-c, 线程死了但 DB 的 status 还是 RUNNING
        // 没这步的话, canTrigger 检查会一直认为"还在跑", admin 无法触发新同步
        // 阈值: 30 分钟 (正常单次同步 < 5 分钟)
        Instant staleCutoff = Instant.now().minusSeconds(30 * 60);
        List<DingTalkAttendanceSyncLog> staleLogs = logRepo.findStaleRunning(staleCutoff);
        if (!staleLogs.isEmpty()) {
            log.warn("[DingTalkAttendanceSync] 发现 {} 条僵尸 RUNNING log (started_at < 30min 前), 自动标记 FAILED",
                    staleLogs.size());
            for (DingTalkAttendanceSyncLog s : staleLogs) {
                s.setStatus("FAILED");
                s.setFinishedAt(Instant.now());
                s.setErrorMessage("同步超时未完成 (启动 >30 分钟未结束, 进程可能已崩溃)");
                s.setErrorDetail("stale guard 自动清理 - 启动时间: " + s.getStartedAt());
                logRepo.save(s);
            }
        }

        DingTalkAttendanceSyncLog slog = new DingTalkAttendanceSyncLog();
        slog.setStartedAt(Instant.now());
        slog.setTriggerType(triggerType);
        slog.setTriggeredBy(triggeredBy != null ? triggeredBy : "admin");
        slog.setStatus("RUNNING");

        LocalDate resolvedTo = (to == null) ? LocalDate.now(ZoneId.systemDefault()) : to;
        LocalDate resolvedFrom;
        String mode;
        if (from == null) {
            // 增量: 起点 = state.last_sync_time 那天
            resolvedFrom = stateRepo.findBySyncKey(SYNC_KEY)
                    .map(s -> s.getLastSyncTime().atZone(ZoneId.systemDefault()).toLocalDate())
                    .orElse(LocalDate.now().minusDays(props.getAttendanceWindowDays()));
            mode = "INCREMENTAL";
        } else {
            resolvedFrom = from;
            mode = "FULL";
        }
        slog.setSyncMode(mode);
        slog.setRangeFrom(resolvedFrom.atStartOfDay(ZoneId.systemDefault()).toInstant());
        slog.setRangeTo(resolvedTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusSeconds(1));
        slog.setLastSyncTime(slog.getRangeTo());

        final DingTalkAttendanceSyncLog running = logRepo.save(slog);
        log.info("[DingTalkAttendanceSync] 触发同步 logId={} mode={} range=[{}..{}] trigger={} by={}",
                running.getId(), mode, resolvedFrom, resolvedTo, triggerType, triggeredBy);

        Thread async = new Thread(() -> {
            try {
                runSync(running.getId(), resolvedFrom, resolvedTo);
            } catch (Exception ex) {
                log.error("[DingTalkAttendanceSync] 后台同步线程异常", ex);
            }
        }, "dingtalk-attendance-sync-" + running.getId());
        async.setDaemon(true);
        // V4.35: 记录 async 线程引用到 log meta - 没这步的话, 未来再发生卡死也无从下手
        // 用 log.error (UncaughtExceptionHandler 备份路径)
        async.setUncaughtExceptionHandler((t, e) -> {
            log.error("[DingTalkAttendanceSync] 后台线程 {} 抛出未捕获异常: {}", t.getName(), e.getMessage(), e);
        });
        async.start();
        return running;
    }

    private void runSync(Long logId, LocalDate from, LocalDate to) {
        DingTalkAttendanceSyncLog slog = logRepo.findById(logId).orElseThrow();
        // V4.35: 硬超时 (10 分钟), 到点强制标记 FAILED 退出后台线程
        // 兜底: stale guard 30min, 期间如果真出卡死, 这里强制中断
        Thread workerThread = Thread.currentThread();
        java.util.concurrent.ScheduledFuture<?> timeoutTask =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "dingtalk-attendance-sync-" + logId + "-watchdog");
                    t.setDaemon(true);
                    return t;
                }).schedule(() -> {
                    log.error("[DingTalkAttendanceSync] logId={} watchdog timeout (10min), 强制中断 worker 线程", logId);
                    workerThread.interrupt();
                }, 10, java.util.concurrent.TimeUnit.MINUTES);
        try {
            // V4.33 改造: 委托给 runSyncTransactional 公共方法
            // 单行 upsert 在 upsertOne 内 @Transactional(REQUIRES_NEW) 自管事务
            SyncOutcome out = runSyncTransactional(slog, from, to);

            // 5) 更新状态
            slog.setCreatedCount(out.created);
            slog.setUpdatedCount(out.updated);
            slog.setDeletedCount(0);  // V4.33 B 任务: 失效检测改新表
            slog.setSkippedCount(out.skipped);
            // 5.1) 暴露"fetched=0"根因: 把 listAttendances 内部记录的 API 错误写到 errorMessage
            //      让 admin 不用翻后端日志也能秒懂"是不是考勤权限没开"
            if (out.fetched == 0) {
                String apiErr = api.consumeLastDetailError();
                if (apiErr != null) {
                    slog.setStatus("FAILED");
                    slog.setErrorMessage("钉钉考勤 API 调用失败: " + apiErr);
                    slog.setErrorDetail("可能原因: 钉钉应用未开通考勤权限 (qyapi_attendance_isv_query_result, qyapi_get_attendance_data)\n" + apiErr);
                } else {
                    slog.setStatus("SUCCESS");  // fetched=0 + 无错误 = 真没数据, 不算 FAILED
                }
            } else {
                slog.setStatus("SUCCESS");
            }
            slog.setFinishedAt(Instant.now());
            logRepo.save(slog);

            DingTalkAttendanceSyncState state = stateRepo.findBySyncKey(SYNC_KEY)
                    .orElseGet(() -> {
                        DingTalkAttendanceSyncState s = new DingTalkAttendanceSyncState();
                        s.setSyncKey(SYNC_KEY);
                        return s;
                    });
            state.setLastSyncTime(slog.getRangeTo());
            state.setLastTotal(out.fetched);
            state.setLastCreated(out.created);
            state.setLastUpdated(out.updated);
            state.setLastDeleted(0);
            state.setUpdatedAt(Instant.now());
            stateRepo.save(state);

            log.info("[DingTalkAttendanceSync] logId={} 成功: fetched={} created={} updated={} skipped={}",
                    logId, out.fetched, out.created, out.updated, out.skipped);
        } catch (Exception e) {
            log.error("[DingTalkAttendanceSync] logId={} 失败: {}", logId, e.getMessage(), e);
            slog.setStatus("FAILED");
            slog.setFinishedAt(Instant.now());
            slog.setErrorMessage(e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            slog.setErrorDetail(sw.toString());
            logRepo.save(slog);
            // V4.34 4.1: 同步失败告警 (邮件 + 钉钉 IM)
            sendFailureAlert(slog);
        } catch (Throwable t) {  // V4.34 4.1: Error 也捕获
            log.error("[DingTalkAttendanceSync] logId={} 灾难性失败: {}", logId, t.getMessage(), t);
            slog.setStatus("FAILED");
            slog.setFinishedAt(Instant.now());
            slog.setErrorMessage(t.getMessage());
            logRepo.save(slog);
            sendFailureAlert(slog);
        } finally {
            // V4.35: 关掉 watchdog (不再需要的中断)
            if (timeoutTask != null) timeoutTask.cancel(false);
        }
    }

    /**
     * V4.34 4.1: 同步失败告警 (邮件 + 钉钉 IM)
     * - 邮件走 MailService (PMO_MAIL_ENABLED 开关)
     * - 钉钉走 DingTalkChannel (走应用机器人)
     * - 异步执行, 不影响 sync 返回
     */
    private void sendFailureAlert(DingTalkAttendanceSyncLog slog) {
        try {
            String subject = "[PMO 告警] 钉钉考勤同步失败 logId=" + slog.getId();
            String body = String.format(
                "同步失败: %s\n范围: %s ~ %s\n触发: %s by %s\n错误: %s",
                slog.getErrorMessage(),
                slog.getRangeFrom(), slog.getRangeTo(),
                slog.getTriggerType(), slog.getTriggeredBy(),
                slog.getErrorDetail() != null ? slog.getErrorDetail().substring(0, Math.min(500, slog.getErrorDetail().length())) : ""
            );
            // 邮件
            try {
                mailService.send(java.util.List.of("admin@company.com"), null, subject, body);
            } catch (Exception e) {
                log.warn("[DingTalkAttendanceSync] 告警邮件发送失败: {}", e.getMessage());
            }
            // 钉钉 IM - V4.34 4.1: 失败兜底, 不阻塞 sync
            try {
                com.company.pmo.module.notification.NotificationMessage msg =
                    new com.company.pmo.module.notification.NotificationMessage(
                        "ATTENDANCE_SYNC_FAILED", subject, body,
                        "ATTENDANCE", (long) slog.getId(), "ATT-SYNC-" + slog.getId(),
                        null, java.util.List.of(1L), null, java.time.Instant.now()
                    );
                dingTalkChannel.send(msg);
            } catch (Exception e) {
                log.warn("[DingTalkAttendanceSync] 告警钉钉发送失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("[DingTalkAttendanceSync] sendFailureAlert 整体异常: {}", e.getMessage());
        }
    }

    /**
     * V4.33 runSync 主逻辑 (A.1–A.7 阶段)
     * 注意:
     *   - 不带 @Transactional (单行事务在 upsertOne 里 REQUIRES_NEW)
     *   - 不写老表 attendanceRepo (老表冻结只读, 给详情抽屉)
     *   - 完整流程留待 A.8–A.14 后续阶段
     */
    private SyncOutcome runSyncTransactional(DingTalkAttendanceSyncLog slog, LocalDate from, LocalDate to) {
        // V4.35: phase timing - 关键阶段加 elapsedMs, 真正卡死时能精确定位
        long t0 = System.currentTimeMillis();
        // A.1: 拉 PMO 已绑定钉钉的 userid 列表
        long t1 = System.currentTimeMillis();
        List<String> userIds = api.listAllUserIdsFromPmo();
        if (userIds.isEmpty()) {
            throw new RuntimeException("没有可同步的用户 (PMO 系统未绑定钉钉 userid)");
        }
        log.info("[DingTalkAttendanceSync] phase=loadUserIds elapsedMs={} count={}",
                System.currentTimeMillis() - t1, userIds.size());

        // A.3: 调钉钉 listRecord 拉原始考勤
        //   钉钉 listRecord 单次跨度 ≤ 7 天 (实测 errcode=41041 时间跨度太大)
        //   我们在 service 层自动分片, 每次 ≤ 7 天, 结果合并
        String fromStr = from.format(DATE_FMT);
        String toStr = to.format(DATE_FMT);
        log.info("[DingTalkAttendanceSync] 拉取考勤 userIds={} range={}..{} (按 7 天分片)",
                userIds.size(), fromStr, toStr);
        t1 = System.currentTimeMillis();
        List<JsonNode> items = fetchAttendancesChunked(userIds, from, to);
        slog.setFetched(items.size());
        log.info("[DingTalkAttendanceSync] phase=listAttendances elapsedMs={} fetched={}",
                System.currentTimeMillis() - t1, items.size());

        // A.5: 第一遍扫描, 按 (userid, workDate) 分组入 Map
        // grouped: userid -> (workDate -> 当天所有 RawRecord)
        Map<String, Map<LocalDate, List<RawRecord>>> grouped = new HashMap<>();
        int skipped = 0;
        for (JsonNode n : items) {
            RawRecord r = parseRaw(n);
            if (r == null) { skipped++; continue; }
            grouped
                    .computeIfAbsent(r.userid, k -> new HashMap<>())
                    .computeIfAbsent(r.workDate, k -> new ArrayList<>())
                    .add(r);
        }

        // A.6 + A.7: 收集 liveKeys (用于 markDeletedIfMissing) 和 allUserids
        Set<String> liveKeys = new HashSet<>();
        Set<String> allUserids = new HashSet<>();
        for (Map.Entry<String, Map<LocalDate, List<RawRecord>>> ue : grouped.entrySet()) {
            allUserids.add(ue.getKey());
            for (LocalDate d : ue.getValue().keySet()) {
                liveKeys.add(ue.getKey() + "#" + d);
            }
        }

        log.info("[DingTalkAttendanceSync] 聚合: users={} userDays={} skipped={}",
                allUserids.size(), liveKeys.size(), skipped);

        // A.8: 一次性查 AppUser (减少 N+1)
        Map<String, AppUser> userMap = userLookup.findByDingtalkUserIdIn(allUserids).stream()
                .filter(u -> u.getDingtalkUserId() != null)
                .collect(Collectors.toMap(
                        AppUser::getDingtalkUserId,
                        u -> u,
                        (a, b) -> a
                ));

        // A.9: 收集 PMO 业务 userId 集合 (供 timesheet JOIN)
        Set<Long> pmoUserIds = userMap.values().stream()
                .map(AppUser::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        log.info("[DingTalkAttendanceSync] PMO 用户绑定: {}/{} (绑定率 {}%)",
                pmoUserIds.size(), allUserids.size(),
                allUserids.isEmpty() ? 0 : (pmoUserIds.size() * 100 / allUserids.size()));

        // A.10: 一次性查 timesheet 填的项目 (native JOIN)
        List<Object[]> rawTs = userLookup.findTimesheetProjects(pmoUserIds, from, to);

        // A.11: 构内存 join: pmoUserId -> (workDate -> Set<projectId>)
        Map<Long, Map<LocalDate, Set<Long>>> tsProjectMap = new HashMap<>();
        for (Object[] row : rawTs) {
            if (row[0] == null || row[1] == null || row[2] == null) continue;
            Long pmoUserId = ((Number) row[0]).longValue();
            LocalDate workDate;
            Object d = row[1];
            if (d instanceof LocalDate) {
                workDate = (LocalDate) d;
            } else if (d instanceof java.sql.Date) {
                workDate = ((java.sql.Date) d).toLocalDate();
            } else {
                continue;
            }
            Long projectId = ((Number) row[2]).longValue();
            tsProjectMap
                    .computeIfAbsent(pmoUserId, k -> new HashMap<>())
                    .computeIfAbsent(workDate, k -> new HashSet<>())
                    .add(projectId);
        }

        // A.12: 收集所有出现过的 projectId
        Set<Long> projectIdSet = tsProjectMap.values().stream()
                .flatMap(m -> m.values().stream())
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        // A.13: 一次性查 Project 拿 id→name (空集时跳过, 防 NPE)
        Map<Long, String> projectNameMap;
        if (projectIdSet.isEmpty()) {
            projectNameMap = Collections.emptyMap();
        } else {
            projectNameMap = projectRepo.findAllById(projectIdSet).stream()
                    .filter(p -> p.getId() != null)
                    .collect(Collectors.toMap(
                            Project::getId,
                            Project::getName,
                            (a, b) -> a
                    ));
        }

        log.info("[DingTalkAttendanceSync] timesheet 关联: pmoUsers={} projects={}",
                pmoUserIds.size(), projectIdSet.size());

        // A.14: 逐天 upsert (双层 for + 单行事务 REQUIRES_NEW)
        Instant now = Instant.now();
        int created = 0, updated = 0;
        for (Map.Entry<String, Map<LocalDate, List<RawRecord>>> ue : grouped.entrySet()) {
            String userid = ue.getKey();
            for (Map.Entry<LocalDate, List<RawRecord>> de : ue.getValue().entrySet()) {
                LocalDate workDate = de.getKey();
                List<RawRecord> recs = de.getValue();

                // 4.2 上班: earliest OnDuty/Before
                List<RawRecord> onDuty = recs.stream()
                        .filter(r -> r.checkType != null && ON_DUTY_TYPES.contains(r.checkType))
                        .filter(r -> r.actualTime != null)
                        .sorted(Comparator.comparing(RawRecord::getActualTime))
                        .collect(Collectors.toList());

                // 4.3 下班: latest OffDuty/After
                List<RawRecord> offDuty = recs.stream()
                        .filter(r -> r.checkType != null && OFF_DUTY_TYPES.contains(r.checkType))
                        .filter(r -> r.actualTime != null)
                        .sorted(Comparator.comparing(RawRecord::getActualTime).reversed())
                        .collect(Collectors.toList());

                RawRecord earliestOn = onDuty.isEmpty() ? null : onDuty.get(0);
                RawRecord latestOff = offDuty.isEmpty() ? null : offDuty.get(0);

                // 4.5 上班 7 字段
                Instant onDutyPlan = earliestOn == null ? null : earliestOn.getPlanTime();
                Instant onDutyActual = earliestOn == null ? null : earliestOn.getActualTime();
                String onDutyResult = earliestOn == null ? "" : nz(earliestOn.getTimeResult());
                String onDutySource = earliestOn == null ? "" : nz(earliestOn.getSource());
                String onDutyLocation = "";  // listRecord 不返回地址
                String onDutyLocationMethod = earliestOn == null ? "" : nz(earliestOn.getLocationMethod());
                String onDutyLocationResult = earliestOn == null ? "" : nz(earliestOn.getLocationResult());

                // 4.6 下班 7 字段
                Instant offDutyPlan = latestOff == null ? null : latestOff.getPlanTime();
                Instant offDutyActual = latestOff == null ? null : latestOff.getActualTime();
                String offDutyResult = latestOff == null ? "" : nz(latestOff.getTimeResult());
                String offDutySource = latestOff == null ? "" : nz(latestOff.getSource());
                String offDutyLocation = "";
                String offDutyLocationMethod = latestOff == null ? "" : nz(latestOff.getLocationMethod());
                String offDutyLocationResult = latestOff == null ? "" : nz(latestOff.getLocationResult());

                // 4.7 异常 + 类型
                boolean isAbnormal = recs.stream()
                        .anyMatch(r -> r.getTimeResult() != null && ABNORMAL_RESULTS.contains(r.getTimeResult()));
                String abnormalTypes = recs.stream()
                        .map(RawRecord::getTimeResult)
                        .filter(Objects::nonNull)
                        .filter(ABNORMAL_RESULTS::contains)
                        .distinct()
                        .collect(Collectors.joining(";"));

                // 4.8 checkCount + rawRecordIds
                int checkCount = recs.size();
                List<String> bizIds = recs.stream()
                        .map(RawRecord::getBizId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                String rawRecordIdsJson;
                try {
                    rawRecordIdsJson = objectMapper.writeValueAsString(bizIds);
                } catch (Exception ex) {
                    log.warn("[DingTalkAttendanceSync] 序列化 rawRecordIds 失败, fallback []: {}", ex.getMessage());
                    rawRecordIdsJson = "[]";
                }

                // 4.9 pmoUserId/userName/dept
                AppUser u = userMap.get(userid);
                Long pmoUserId = u == null ? null : u.getId();
                String userName = u == null ? null : u.getFullName();
                Long departmentId = u == null ? null : u.getDepartmentId();

                // 4.10 projectIds / projectNames
                String projectIds = "";
                String projectNames = "";
                if (pmoUserId != null) {
                    Map<LocalDate, Set<Long>> dayMap = tsProjectMap.get(pmoUserId);
                    if (dayMap != null) {
                        Set<Long> pids = dayMap.get(workDate);
                        if (pids != null && !pids.isEmpty()) {
                            List<Long> sortedPids = pids.stream().sorted().collect(Collectors.toList());
                            projectIds = sortedPids.stream().map(String::valueOf).collect(Collectors.joining(","));
                            projectNames = sortedPids.stream()
                                    .map(pid -> projectNameMap.getOrDefault(pid, ""))
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.joining(","));
                        }
                    }
                }

                // 4.11 dingtalkUpdatedAt
                Instant dingtalkUpdatedAt = recs.stream()
                        .map(RawRecord::getDingtalkUpdatedAt)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null);

                // 4.12 调 upsertOne (REQUIRES_NEW 单行事务) + 累计 + try-catch
                // V4.34: isMakeup 升级 - 调 getSourceType 拿 sourceType 判定
                boolean isMakeup = false;
                if (recs.size() > 0 && recs.get(0).getBizId() != null) {
                    String sourceType = fetchSourceTypeWithRetry(userid, recs.get(0).getBizId());
                    isMakeup = "UN_APPROVED".equals(sourceType);
                }
                // V4.34: workDuration 计算 (分钟)
                Integer workDuration = null;
                if (onDutyActual != null && offDutyActual != null) {
                    long sec = offDutyActual.getEpochSecond() - onDutyActual.getEpochSecond();
                    workDuration = (int) (sec / 60);
                }
                try {
                    int affected = dailyUpserter.upsertViaProxy(
                            userid, workDate,
                            onDutyPlan, onDutyActual, onDutyResult, onDutySource,
                            onDutyLocation, onDutyLocationMethod, onDutyLocationResult,
                            offDutyPlan, offDutyActual, offDutyResult, offDutySource,
                            offDutyLocation, offDutyLocationMethod, offDutyLocationResult,
                            checkCount, isMakeup, isAbnormal, abnormalTypes,
                            projectIds, projectNames,
                            pmoUserId, userName, departmentId,
                            rawRecordIdsJson, dingtalkUpdatedAt, now, now,
                            workDuration
                    );
                    if (affected == 1) created++;
                    else if (affected == 2) updated++;
                } catch (Exception ex) {
                    log.warn("[DingTalkAttendanceSync] upsert 失败 userid={} workDate={}: {}",
                            userid, workDate, ex.getMessage());
                }
            }
        }

        // B 任务: 失效检测 (liveKeys 已收集, 改用新表)
        int deletedCount = markDeletedIfMissing(liveKeys, from, to);

        log.info("[DingTalkAttendanceSync] upsert 完成: created={} updated={} deleted={} totalElapsedMs={}",
                created, updated, deletedCount, System.currentTimeMillis() - t0);

        SyncOutcome out = new SyncOutcome();
        out.fetched = items.size();
        out.created = created;
        out.updated = updated;
        out.deleted = deletedCount;
        out.skipped = skipped;
        return out;
    }

    /**
     * null-safe 字符串兜底 (空字符串代替 null, 对应新表 NOT NULL DEFAULT '')
     */
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * V4.36: 钉钉 listRecord 单次跨度 ≤ 7 天, 自动按 7 天分片循环拉取
     *   原始 1 次调用拆成 N 次, 每次 from..to 跨度 ≤ 7 天, 结果合并返回
     *   任何 1 次分片失败 → 抛异常, 让上层标 FAILED 并暴露错误
     */
    private List<JsonNode> fetchAttendancesChunked(List<String> userIds, LocalDate from, LocalDate to) {
        final int MAX_DAYS = 7;  // 钉钉 listRecord 实测上限
        List<JsonNode> merged = new ArrayList<>();
        if (from == null || to == null || !from.isBefore(to.plusDays(1))) {
            return merged;
        }
        int chunks = 0;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate chunkTo = cursor.plusDays(MAX_DAYS - 1);
            if (chunkTo.isAfter(to)) chunkTo = to;
            String fromStr = cursor.format(DATE_FMT);
            String toStr = chunkTo.format(DATE_FMT);
            log.info("[DingTalkAttendanceSync] 拉取分片 {}/? range={}..{}", chunks + 1, fromStr, toStr);
            List<JsonNode> chunk = api.listAttendances(userIds, fromStr, toStr);
            merged.addAll(chunk);
            chunks++;
            cursor = chunkTo.plusDays(1);
        }
        log.info("[DingTalkAttendanceSync] 7 天分片共 {} 段, 累计 {} 条", chunks, merged.size());
        return merged;
    }

    /**
     * V4.34: 调 getSourceType 拿 sourceType, 退避 3 次 (D1.9 决策)
     * 失败 → isMakeup=false 兜底 (D1.7 + D1.13 决策)
     */
    private String fetchSourceTypeWithRetry(String userid, String bizId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return api.getSourceType(userid, bizId);
            } catch (Exception e) {
                if (attempt == 2) {
                    log.warn("[DingTalkAttendanceSync] getSourceType 失败 (3 次): userid={} bizId={}: {}",
                            userid, bizId, e.getMessage());
                    return "COMPLETE";
                }
                try {
                    Thread.sleep(500L * (1L << attempt));  // 500/1000/2000 ms
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "COMPLETE";
                }
            }
        }
        return "COMPLETE";
    }

    /**
     * V4.33 runSyncTransactional 返回值
     */
    private static class SyncOutcome {
        int fetched;
        int created;
        int updated;
        int deleted;
        int skipped;
    }

    /**
     * V4.33 单行 upsert (REQUIRES_NEW 单行事务)
     * 必须在 public 方法上 @Transactional, AOP 才能增强 (private 不生效)
     * B.1 决策: 单行事务, 失败不影响其他行
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int upsertOne(
            String userid, LocalDate workDate,
            Instant onDutyPlan, Instant onDutyActual, String onDutyResult, String onDutySource,
            String onDutyLocation, String onDutyLocationMethod, String onDutyLocationResult,
            Instant offDutyPlan, Instant offDutyActual, String offDutyResult, String offDutySource,
            String offDutyLocation, String offDutyLocationMethod, String offDutyLocationResult,
            Integer checkCount, Boolean isMakeup, Boolean isAbnormal, String abnormalTypes,
            String projectIds, String projectNames,
            Long pmoUserId, String userName, Long departmentId,
            String rawRecordIds, Instant dingtalkUpdatedAt, Instant syncedAt,
            Instant now, Integer workDuration) {
        return dailyRepo.upsertByUseridAndWorkDate(
                userid, workDate,
                onDutyPlan, onDutyActual, onDutyResult, onDutySource,
                onDutyLocation, onDutyLocationMethod, onDutyLocationResult,
                offDutyPlan, offDutyActual, offDutyResult, offDutySource,
                offDutyLocation, offDutyLocationMethod, offDutyLocationResult,
                checkCount, isMakeup, isAbnormal, abnormalTypes,
                projectIds, projectNames,
                pmoUserId, userName, departmentId,
                rawRecordIds, dingtalkUpdatedAt, syncedAt,
                workDuration, now);
    }

    /**
     * V4.33 失效检测: 区间内新表的非 deleted 行, 但 liveKeys 中已不存在 → 标记 deleted
     * liveKeys 格式: "userid#yyyy-MM-dd"
     * 区间外的不动 (保护历史数据)
     * 老表不再处理 (冻结只读, 详情抽屉用)
     */
    private int markDeletedIfMissing(Set<String> liveKeys, LocalDate from, LocalDate to) {
        Page<DingTalkAttendanceDaily> page = dailyRepo.findAllActive(
                PageRequest.of(0, 10000, Sort.by(Sort.Direction.DESC, "workDate")));
        int deleted = 0;
        for (DingTalkAttendanceDaily d : page.getContent()) {
            if (d.getWorkDate() == null) continue;
            if (d.getWorkDate().isBefore(from) || d.getWorkDate().isAfter(to)) continue;
            String key = d.getUserid() + "#" + d.getWorkDate();
            if (!liveKeys.contains(key)) {
                d.setDeleted(true);
                d.setUpdatedAt(Instant.now());
                dailyRepo.save(d);
                deleted++;
            }
        }
        return deleted;
    }

    private LocalDate parseLocalDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            // 钉钉 attendance/listRecord 接口 workDate 是毫秒时间戳 (e.g. 1782835200000)
            // 兼容 yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / millis 三种
            String head = s.length() >= 10 ? s.substring(0, 10) : s;
            return LocalDate.parse(head, DATE_FMT);
        } catch (Exception e) {
            // 退化: 当作 millis 时间戳
            try {
                long ms = Long.parseLong(s.trim());
                if (ms > 0) return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate();
            } catch (Exception ignore) {}
            return null;
        }
    }

    private LocalDate parseLocalDateField(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        // 数字: millis (钉钉 listRecord 用 millis)
        if (v.canConvertToLong()) {
            long ms = v.asLong(0);
            if (ms <= 0) return null;
            return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate();
        }
        // 字符串: 各种日期格式
        return parseLocalDate(v.asText(""));
    }

    /**
     * V4.33 解析一条 JsonNode → RawRecord
     * 返回 null = 该记录无效 (bizId/userid/workDate 任一为空), caller 应 skipped++
     */
    private RawRecord parseRaw(JsonNode n) {
        // 1) bizId: listRecord 用 bizId, 兜底 recordId / id
        String bizId = n.path("bizId").asText("");
        if (bizId.isEmpty()) bizId = n.path("recordId").asText("");
        if (bizId.isEmpty()) bizId = n.path("id").asText("");
        if (bizId.isEmpty()) return null;

        // 2) userid
        String userid = n.path("userId").asText("");
        if (userid.isEmpty()) userid = n.path("userid").asText("");
        if (userid.isEmpty()) return null;

        // 3) workDate: listRecord workDate 是 millis, 兜底 baseCheckTime
        LocalDate workDate = parseLocalDateField(n, "workDate");
        if (workDate == null) workDate = parseLocalDateField(n, "baseCheckTime");
        if (workDate == null) return null;

        return new RawRecord(
                bizId, userid, workDate,
                n.path("checkType").asText(""),
                n.path("source").asText(""),
                n.path("timeResult").asText(""),
                n.path("locationMethod").asText(""),
                n.path("locationResult").asText(""),
                parseInstantField(n, "planCheckTime"),
                parseInstantField(n, "userCheckTime"),
                parseInstantField(n, "baseCheckTime"),
                parseInstantField(n, "updateTime")
        );
    }

    private Instant parseInstantField(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        // 数字: millis
        if (v.canConvertToLong()) {
            long ms = v.asLong(0);
            return ms > 0 ? Instant.ofEpochMilli(ms) : null;
        }
        // 字符串: ISO-8601
        String s = v.asText("");
        if (s.isEmpty()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception ex) {
            try {
                return java.time.OffsetDateTime.parse(s).toInstant();
            } catch (Exception ex2) {
                return null;
            }
        }
    }

    // ============================================================
    // 查询接口 (供前端)
    // V4.33 改造: list/stats 切到新表 dingtalk_attendance_daily
    // (老表 dingtalk_attendance 冻结只读, 仅给详情抽屉 /raw 用)
    // ============================================================

    @Transactional(readOnly = true)
    public Page<DingTalkAttendanceDaily> list(int page, int size) {
        return list(page, size, null, null, null, null);
    }

    /**
     * V4.33+ 列表 (带筛选)
     * @param dateFrom ISO yyyy-MM-dd (可选, null 不过滤)
     * @param dateTo   ISO yyyy-MM-dd (可选)
     * @param useridKeyword 模糊匹配 userid 或 userName (可选)
     * @param isAbnormal  true=只看异常, false=只看正常, null=全部
     */
    @Transactional(readOnly = true)
    public Page<DingTalkAttendanceDaily> list(int page, int size,
                                               String dateFrom, String dateTo,
                                               String useridKeyword,
                                               Boolean isAbnormal) {
        LocalDate from = (dateFrom == null || dateFrom.isBlank()) ? null : LocalDate.parse(dateFrom);
        LocalDate to   = (dateTo   == null || dateTo.isBlank())   ? null : LocalDate.parse(dateTo);
        String kw = (useridKeyword == null) ? "" : useridKeyword.trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "workDate"));
        return dailyRepo.findWithFilters(from, to, kw, isAbnormal, pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        long total = dailyRepo.countActive();
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        long monthCount = dailyRepo.countByDateRange(now.withDayOfMonth(1), now);
        long abnormalMonth = dailyRepo.countAbnormalByDateRange(now.withDayOfMonth(1), now);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("thisMonth", monthCount);
        m.put("abnormalThisMonth", abnormalMonth);
        m.put("abnormalRate", monthCount == 0 ? 0 : (abnormalMonth * 100 / monthCount));
        return m;
    }

    @Transactional(readOnly = true)
    public DingTalkAttendanceSyncState getState() {
        return stateRepo.findBySyncKey(SYNC_KEY).orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<DingTalkAttendanceSyncLog> logs(int page, int size) {
        return logRepo.findAllByOrderByStartedAtDesc(PageRequest.of(page, size));
    }
}
