package com.hex.projectgovern.module.dingtalk;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.security.RequireRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 钉钉请休假同步管理 API (P5)
 *
 * - 限 PMO_ADMIN / ADMIN
 * - 手动触发同步 (异步, 立即返回 RUNNING 日志)
 * - 查询请休假列表 / 同步状态 / 同步日志
 */
@RestController
@RequestMapping("/admin/dingtalk/leave")
@RequireRoles.Admin
@RequiredArgsConstructor
public class DingTalkLeaveAdminController {

    private final DingTalkLeaveSyncService sync;
    private final DingTalkLeaveRepository leaveRepo;

    /**
     * 手动触发同步 (默认增量)
     * @param fullSync=true 全量同步(最近6个月); false 增量同步(默认)
     */
    @PostMapping("/sync/trigger")
    public DingTalkLeaveSyncLog trigger(
            @RequestParam(required = false) String operator,
            @RequestParam(defaultValue = "false") boolean fullSync) {
        return sync.syncNow("MANUAL", operator != null ? operator : "admin", fullSync);
    }

    /** 同步状态 (上次同步时间 + 累计数) */
    @GetMapping("/sync/state")
    public DingTalkLeaveSyncState state() {
        return sync.getState();
    }

    /** 同步日志 */
    @GetMapping("/sync/logs")
    public Page<DingTalkLeaveSyncLog> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sync.logs(page, size);
    }

    /** 统计 */
    @GetMapping("/stats")
    public ApiResponse<Object> stats() {
        return ApiResponse.ok(sync.stats());
    }

    /** 请休假列表 */
    @GetMapping
    public Page<DingTalkLeave> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sync.list(page, size);
    }

    /** 详情 */
    @GetMapping("/{id}")
    public ApiResponse<DingTalkLeave> get(@PathVariable Long id) {
        return leaveRepo.findById(id)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new com.hex.projectgovern.common.exception.BusinessException(404, "leave not found: " + id));
    }
}
