package com.company.zhiyu.module.dingtalk;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.security.RequireRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 钉钉考勤同步管理 API (V4.30 + V4.33)
 *
 * - 限 PMO_ADMIN / ADMIN
 * - 手动触发同步 (异步) — 支持指定时间范围
 * - 查询考勤列表 / 同步状态 / 同步日志
 * - V4.33 详情抽屉: /raw?ids=... 反查老表原始打卡
 */
@RestController
@RequestMapping("/admin/dingtalk/attendance")
@RequireRoles.Admin
@RequiredArgsConstructor
public class DingTalkAttendanceAdminController {

    private final DingTalkAttendanceSyncService sync;
    private final DingTalkAttendanceRepository attendanceRepo;
    private final DingTalkAttendanceDailyRepository dailyRepo;

    /**
     * 手动触发同步
     * @param from 拉取起点 (含, yyyy-MM-dd); 留空 = 增量
     * @param to   拉取终点 (含, yyyy-MM-dd); 留空 = 今天
     */
    @PostMapping("/sync/trigger")
    public DingTalkAttendanceSyncLog trigger(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return sync.syncNow("MANUAL", operator != null ? operator : "admin", from, to);
    }

    @GetMapping("/sync/state")
    public DingTalkAttendanceSyncState state() {
        return sync.getState();
    }

    @GetMapping("/sync/logs")
    public Page<DingTalkAttendanceSyncLog> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sync.logs(page, size);
    }

    @GetMapping("/stats")
    public ApiResponse<Object> stats() {
        return ApiResponse.ok(sync.stats());
    }

    @GetMapping
    public Page<DingTalkAttendanceDaily> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String useridKeyword,
            @RequestParam(required = false) Boolean isAbnormal) {
        return sync.list(page, size, dateFrom, dateTo, useridKeyword, isAbnormal);
    }

    // ============================================================
    // V4.33 详情抽屉
    // ============================================================

    /** 详情抽屉防大 IN 攻击: 限制最大 100 个 recordId */
    private static final int RAW_IDS_MAX = 100;

    /**
     * 详情抽屉: 查某日聚合行的原始打卡记录
     *
     * 入参: ids=recordId1,recordId2,recordId3
     * 行为: 走老表 dingtalk_attendance (冻结只读, V4.33 不再写)
     * 来源: 新表 raw_record_ids JSON 数组
     */
    @GetMapping("/raw")
    public ApiResponse<List<DingTalkAttendance>> raw(
            @RequestParam("ids") String ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.ok(java.util.Collections.emptyList());
        }
        List<String> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (idList.isEmpty()) {
            return ApiResponse.ok(java.util.Collections.emptyList());
        }
        if (idList.size() > RAW_IDS_MAX) {
            return ApiResponse.fail(4001, "一次最多查 " + RAW_IDS_MAX + " 条原始记录");
        }
        return ApiResponse.ok(attendanceRepo.findAllByRecordIdIn(idList));
    }
}
