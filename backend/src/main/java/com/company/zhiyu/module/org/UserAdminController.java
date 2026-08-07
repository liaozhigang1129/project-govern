package com.company.zhiyu.module.org;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.audit.AuditLog;
import com.company.zhiyu.common.security.RequireRoles;
import com.company.zhiyu.common.security.SecurityUtils;
import com.company.zhiyu.module.org.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * L1-1 用户管理 Controller
 * - 11 个端点 (CRUD + 重置密码 + 解锁 + 改密 + 离职 + 详情)
 * - @RequireRoles.Admin: 写操作 (PMO_ADMIN/ADMIN)
 * - @RequireRoles.Read:  只读 (任意已登录)
 *
 * 端点清单:
 *   GET    /api/users                 分页 + 搜索
 *   GET    /api/users/{id}            详情 (自己看自己明文, 他人脱敏)
 *   POST   /api/users                 新建
 *   PUT    /api/users/{id}            更新
 *   POST   /api/users/{id}/reset-password    管理员重置
 *   POST   /api/users/me/change-password     自己改密
 *   POST   /api/users/{id}/unlock            解锁
 *   POST   /api/users/{id}/offboard          离职 (软删 + 交接)
 *   GET    /api/users/me              自己的资料
 *   PATCH  /api/users/me              自己改 (fullName/email/phone)
 *   GET    /api/users/options         简表 (id+username+fullName, 给其他模块下拉用)
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users Admin (L1-1)", description = "用户管理 — 增删改查/重置密码/解锁/离职")
public class UserAdminController {

    private final UserService userService;
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final DepartmentRepository deptRepo;
    private final SecurityUtils securityUtils;

    // ============================================================
    //  1) 列表 — 任意已登录
    // ============================================================
    @GetMapping
    @RequireRoles.Read
    @Operation(summary = "用户分页搜索 (keyword/dept/role/enabled)")
    public ApiResponse<Page<UserListItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false, defaultValue = "true") Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,desc") String sort
    ) {
        return ApiResponse.ok(userService.search(
                keyword, departmentId, roleCode,
                Boolean.TRUE.equals(enabled), page, Math.min(size, 100), sort));
    }

    // ============================================================
    //  1.5) V4.14: 按部门 ID 列表筛选 (含子部门)
    // ============================================================
    @PostMapping("/by-departments")
    @RequireRoles.Read
    @Operation(summary = "按部门 ID 列表筛选用户 (V4.14 部门树)")
    public ApiResponse<Page<UserListItem>> byDepartments(@org.springframework.web.bind.annotation.RequestBody ByDepartmentsRequest req) {
        return ApiResponse.ok(userService.searchByDepartments(
                req.keyword(), req.departmentIds(), Boolean.TRUE.equals(req.includeSubDepts()),
                req.page() != null ? req.page() : 0,
                req.size() != null ? Math.min(req.size(), 100) : 20,
                req.sort() != null ? req.sort() : "id,desc"));
    }

    // V4.14: 入参
    public record ByDepartmentsRequest(
            String keyword,
            java.util.List<Long> departmentIds,
            Boolean includeSubDepts,
            Integer page,
            Integer size,
            String sort
    ) {}

    // ============================================================
    //  2) /me 系列 — 必须放在 /{id} 之前, 否则会被路由吃掉
    // ============================================================
    @GetMapping("/me")
    @RequireRoles.Read
    @Operation(summary = "我的资料 (明文)")
    public ApiResponse<UserListItem> me() {
        return ApiResponse.ok(userService.detail(securityUtils.currentUserId()));
    }

    @PatchMapping("/me")
    @RequireRoles.Read
    @Operation(summary = "改自己的 fullName/email/phone (username 不可改)")
    public ApiResponse<UserListItem> updateMe(@RequestBody @Valid UserUpdateRequest req) {
        Long me = securityUtils.currentUserId();
        // 只允许改这几个字段
        UserUpdateRequest whitelist = new UserUpdateRequest(
                req.fullName(), req.email(), req.phone(),
                null, null, null, null, null, null, null);
        return ApiResponse.ok(UserListItem.from(
                userService.update(me, whitelist),
                deptRepo.findById(me).map(Department::getName).orElse(""),
                "",
                List.of(),
                req.phone() // 自己看明文
        ));
    }

    @PostMapping("/me/change-password")
    @RequireRoles.Read
    @AuditLog(module = "USER", action = "PASSWORD_CHANGED")
    @Operation(summary = "自己改密")
    public ApiResponse<Void> changeOwnPassword(@RequestBody @Valid ChangePasswordRequest req) {
        userService.changePassword(securityUtils.currentUserId(), req);
        return ApiResponse.ok();
    }

    // ============================================================
    //  3) 详情
    // ============================================================
    @GetMapping("/{id}")
    @RequireRoles.Read
    @Operation(summary = "用户详情 (自己看自己明文, 他人脱敏)")
    public ApiResponse<UserListItem> detail(@PathVariable Long id) {
        return ApiResponse.ok(userService.detail(id));
    }

    // ============================================================
    //  4) 新建
    // ============================================================
    @PostMapping
    @RequireRoles.Admin
    @AuditLog(module = "USER", action = "CREATE")
    @Operation(summary = "新建用户 (PMO_ADMIN)")
    public ApiResponse<UserListItem> create(@RequestBody @Valid UserCreateRequest req) {
        var u = userService.create(req);
        return ApiResponse.ok(userService.detail(u.getId()));
    }

    // ============================================================
    //  5) 更新
    // ============================================================
    @PutMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "USER", action = "UPDATE")
    @Operation(summary = "更新用户 (PMO_ADMIN)")
    public ApiResponse<UserListItem> update(@PathVariable Long id,
                                            @RequestBody @Valid UserUpdateRequest req) {
        userService.update(id, req);
        return ApiResponse.ok(userService.detail(id));
    }

    // ============================================================
    //  6) 管理员重置密码
    // ============================================================
    @PostMapping("/{id}/reset-password")
    @RequireRoles.Admin
    @AuditLog(module = "USER", action = "PASSWORD_RESET")
    @Operation(summary = "管理员重置密码 (PMO_ADMIN, 发邮件, 吊销 token)")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @RequestBody @Valid PasswordResetRequest req) {
        userService.resetPassword(id, req);
        return ApiResponse.ok();
    }

    // ============================================================
    //  7) 解锁
    // ============================================================
    @PostMapping("/{id}/unlock")
    @RequireRoles.Admin
    @AuditLog(module = "USER", action = "UNLOCK")
    @Operation(summary = "解锁账户 (清失败计数 + lockedUntil)")
    public ApiResponse<Void> unlock(@PathVariable Long id) {
        userService.unlock(id);
        return ApiResponse.ok();
    }

    // ============================================================
    //  8) 离职
    // ============================================================
    @PostMapping("/{id}/offboard")
    @RequireRoles.Admin
    @AuditLog(module = "USER", action = "OFFBOARD")
    @Operation(summary = "离职 (软删 + 交接项目/WBS owner + 吊销 token)")
    public ApiResponse<Void> offboard(@PathVariable Long id,
                                      @RequestBody @Valid OffboardRequest req) {
        userService.offboard(id, req);
        return ApiResponse.ok();
    }

    // ============================================================
    //  V4.12: 批量操作
    // ============================================================
    @PatchMapping("/bulk-enabled")
    @RequireRoles.Admin
    @AuditLog(module = "USER", action = "BULK_ENABLED", extractResourceId = false)
    @Operation(summary = "批量启/停用户")
    public ApiResponse<java.util.Map<String, Object>> bulkSetEnabled(
            @RequestBody @Valid BulkUserRequest req) {
        return ApiResponse.ok(userService.bulkSetEnabled(req.ids(), req.enabled()));
    }

    @PatchMapping("/bulk-department")
    @RequireRoles.Admin
    @AuditLog(module = "USER", action = "BULK_DEPT", extractResourceId = false)
    @Operation(summary = "批量调整用户部门")
    public ApiResponse<java.util.Map<String, Object>> bulkSetDepartment(
            @RequestBody @Valid BulkUserRequest req) {
        int n = userService.bulkSetDepartment(req.ids(), req.departmentId());
        return ApiResponse.ok(java.util.Map.of("affected", n));
    }

    @PatchMapping("/bulk-unlock")
    @RequireRoles.Admin
    @AuditLog(module = "USER", action = "BULK_UNLOCK", extractResourceId = false)
    @Operation(summary = "批量解锁用户")
    public ApiResponse<java.util.Map<String, Object>> bulkUnlock(
            @RequestBody @Valid BulkUserRequest req) {
        int n = userService.bulkUnlock(req.ids());
        return ApiResponse.ok(java.util.Map.of("affected", n));
    }

    // ============================================================
    //  9) 简表 — 给其他模块下拉用
    // ============================================================
    @GetMapping("/options")
    @RequireRoles.Read
    @Operation(summary = "用户简表 (id/username/fullName, 给下拉)")
    public ApiResponse<?> options() {
        return ApiResponse.ok(userRepo.findAll().stream()
                .filter(u -> u.isEnabled() && !u.isDeleted())
                .map(u -> Map.of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "fullName", u.getFullName(),
                        "primaryRoleCode", u.getPrimaryRole() == null
                                ? "" : u.getPrimaryRole().getCode()))
                .toList());
    }

    // ============================================================
    //  10) 导出 Excel — 账号 / 姓名 / 部门(全路径) / 手机 / 邮箱 / 岗位
    //   - 不走 ApiResponse 壳, 直接流式 ResponseEntity<byte[]> (SXSSFWorkbook)
    //   - 与列表查询参数对齐 (keyword/departmentId/roleCode/enabled)
    //   - 默认只导 enabled=true & deleted=false (与列表默认一致)
    // ============================================================
    @GetMapping(path = "/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RequireRoles.Read
    @Operation(summary = "导出用户列表为 xlsx (与当前筛选一致, 上限 10 万)")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false, defaultValue = "true") Boolean enabled
    ) throws java.io.IOException {
        // 拉数据 + 转 xlsx 字节
        byte[] body = userService.exportXlsx(
                keyword, departmentId, roleCode,
                Boolean.TRUE.equals(enabled),
                100_000);

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"users_" + ts + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(body.length)
                .body(body);
    }
}
