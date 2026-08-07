package com.company.zhiyu.module.dingtalk;

import com.company.zhiyu.module.admin.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 钉钉通讯录配置 (V2.13 Phase 1)
 *
 * 数据源: system_config 表 (integration group, 6 个 key)
 *   - integration.dingtalk.enabled
 *   - integration.dingtalk.app_key
 *   - integration.dingtalk.app_secret
 *   - integration.dingtalk.agent_id
 *   - integration.dingtalk.sync_cron
 *   - integration.dingtalk.auto_create_user
 *
 * 设计:
 *  - 不做 @ConfigurationProperties 缓存, 每次 getX() 走 SystemConfigService (内存有 60s 缓存)
 *  - isConfigured() = appKey + appSecret 都不为空 (调 OpenAPI 必要)
 *  - 所有字符串 trim(), 避免管理后台输入空格
 */
@Component
@RequiredArgsConstructor
public class DingTalkProperties {

    private final SystemConfigService sys;

    public boolean isEnabled()      { return sys.getBoolean("integration.dingtalk.enabled", false); }
    public String  getAppKey()      { return trim(sys.getString("integration.dingtalk.app_key", "")); }
    public String  getAppSecret()   { return trim(sys.getString("integration.dingtalk.app_secret", "")); }
    public String  getAgentId()     { return trim(sys.getString("integration.dingtalk.agent_id", "")); }
    public String  getSyncCron()    { return trim(sys.getString("integration.dingtalk.sync_cron", "0 0 2 * * *")); }
    public boolean isAutoCreate()   { return sys.getBoolean("integration.dingtalk.auto_create_user", true); }

    // V2.14 请休假 / OA 审批同步配置
    /** 请休假 (及出差/外出/加班等) 的审批模板 processCode 列表, 逗号分隔. 默认 = 仅"请假". */
    public List<String> getLeaveProcessCodes() {
        String s = trim(sys.getString("integration.dingtalk.leave_process_codes",
                "PROC-325BD729-5E99-4E99-9534-A3CB99617938"));
        if (s.isEmpty()) return List.of();
        return Stream.of(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }

    /** 单次窗口查询天数 (不能超过 120 天). 默认 60. */
    public int getLeaveWindowDays()  { int v = sys.getInt("integration.dingtalk.leave_window_days", 60); return v <= 0 || v > 120 ? 60 : v; }

    /** 是否拉取"请假"以外的模板 (出差/外出/加班). 开关默认 true. */
    public boolean isLeaveIncludeOtherTypes() { return sys.getBoolean("integration.dingtalk.leave_include_other_types", true); }

    // V4.30 考勤同步配置
    /** 考勤同步时间范围(天). 默认 14 = 2 周. 用于"按窗口全量"模式. */
    public int getAttendanceWindowDays() { int v = sys.getInt("integration.dingtalk.attendance_window_days", 14); return v <= 0 ? 14 : v; }

    /** 考勤定时同步 cron 表达式. 默认每周日 03:00 (Spring 6 段式: 秒 分 时 日 月 周) */
    public String getAttendanceCron() { return trim(sys.getString("integration.dingtalk.attendance_cron", "0 0 3 ? * SUN")); }

    public boolean isConfigured() {
        return !getAppKey().isEmpty() && !getAppSecret().isEmpty();
    }

    /** 同时 enabled + configured 才能同步 */
    public boolean canSync() {
        return isEnabled() && isConfigured();
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
