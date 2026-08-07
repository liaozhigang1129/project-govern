package com.company.pmo.module.dingtalk;

import com.company.pmo.common.security.RequireRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 钉钉同步管理 API (V2.13 Phase 1)
 *
 * - 限 PMO_ADMIN / ADMIN
 * - 手动触发同步 (同步执行, 5000 用户约 30s)  → 查 sync_log 看结果
 * - 看同步历史
 * - 一键测通: 校验 appKey/secret 是否能拿到 accessToken
 */
@RestController
@RequestMapping("/admin/dingtalk")
@RequireRoles.Admin
@RequiredArgsConstructor
public class DingTalkSyncAdminController {

    private final DingTalkSyncService sync;
    private final DingTalkSyncLogRepository logRepo;
    private final DingTalkApiClient api;
    private final DingTalkProperties props;

    /** 手动触发一次同步 (同步执行, 5000 用户约 30s) */
    @PostMapping("/sync/trigger")
    public DingTalkSyncLog trigger(@RequestParam(required = false) String operator) {
        return sync.syncNow("MANUAL", operator != null ? operator : "admin");
    }

    /** 最近 50 条同步日志 */
    @GetMapping("/sync/logs")
    public List<DingTalkSyncLog> logs() {
        return logRepo.findTop50ByOrderByStartedAtDesc();
    }

    /** 当前配置状态 (给 admin 后台显示, 不含 secret) */
    @GetMapping("/config/status")
    public ConfigStatus configStatus() {
        return new ConfigStatus(
            props.isEnabled(),
            props.isConfigured(),
            props.getAppKey().isEmpty() ? null : mask(props.getAppKey()),
            props.getAgentId().isEmpty() ? null : props.getAgentId(),
            props.getSyncCron(),
            props.isAutoCreate()
        );
    }

    /**
     * 一键测通: 用当前配置的 appKey/secret 调一次 /v1.0/oauth2/accessToken
     * - 不污染 sync_log
     * - 不写入 token 缓存
     * - 返回 5 类诊断: OK / EMPTY_CONFIG / BAD_CREDENTIAL / HTTP_400 / NETWORK_ERROR / REMOTE_5xx
     */
    @PostMapping("/test-connection")
    public DingTalkApiClient.TestResult testConnection() {
        return api.testConnection();
    }

    private static String mask(String s) {
        if (s.length() <= 4) return "****";
        return s.substring(0, 2) + "****" + s.substring(s.length() - 2);
    }

    public record ConfigStatus(
        boolean enabled, boolean configured,
        String appKeyMasked, String agentId,
        String syncCron, boolean autoCreate
    ) {}
}
