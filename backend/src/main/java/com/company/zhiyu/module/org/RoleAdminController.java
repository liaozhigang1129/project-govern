package com.company.zhiyu.module.org;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.audit.AuditLog;
import com.company.zhiyu.common.security.RequireRoles;
import com.company.zhiyu.module.org.dto.BulkEnabledRequest;
import com.company.zhiyu.module.org.dto.RoleCreateRequest;
import com.company.zhiyu.module.org.dto.RoleListItem;
import com.company.zhiyu.module.org.dto.RoleUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * L1-2 角色管理 Controller (V4.12 升级)
 *
 * 端点:
 *   GET    /api/roles                列表 (?includeDisabled=true 包含已停用)
 *   GET    /api/roles/options        简表 (下拉用, 仅启用)
 *   GET    /api/roles/{id}           详情
 *   POST   /api/roles                新建 (PMO_ADMIN/ADMIN)
 *   PUT    /api/roles/{id}           更新 (PMO_ADMIN/ADMIN)
 *   PATCH  /api/roles/{id}/enabled   启停 (PMO_ADMIN/ADMIN)
 *   PATCH  /api/roles/bulk-enabled   批量启停 (PMO_ADMIN/ADMIN)
 *   DELETE /api/roles/{id}           删除 (PMO_ADMIN/ADMIN, 内置角色不可)
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "Roles Admin (L1-2)", description = "角色管理 — 增删改查/启停/下拉")
public class RoleAdminController {

    private final RoleService roleService;

    // ============================================================
    //  只读 — 任意已登录
    // ============================================================
    @GetMapping
    @RequireRoles.Read
    @Operation(summary = "角色列表")
    public ApiResponse<List<RoleListItem>> list(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(roleService.list(includeDisabled));
    }

    @GetMapping("/options")
    @RequireRoles.Read
    @Operation(summary = "角色简表 (下拉用)")
    public ApiResponse<List<RoleService.RoleOption>> options() {
        return ApiResponse.ok(roleService.options());
    }

    @GetMapping("/{id}")
    @RequireRoles.Read
    @Operation(summary = "角色详情")
    public ApiResponse<RoleListItem> get(@PathVariable Long id) {
        return ApiResponse.ok(roleService.get(id));
    }

    // ============================================================
    //  写 — PMO_ADMIN / ADMIN
    // ============================================================
    @PostMapping
    @RequireRoles.Admin
    @AuditLog(module = "ROLE", action = "CREATE")
    @Operation(summary = "新建自定义角色")
    public ApiResponse<RoleListItem> create(@Valid @RequestBody RoleCreateRequest req) {
        return ApiResponse.ok(roleService.create(req));
    }

    @PutMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "ROLE", action = "UPDATE")
    @Operation(summary = "更新角色")
    public ApiResponse<RoleListItem> update(@PathVariable Long id,
                                            @Valid @RequestBody RoleUpdateRequest req) {
        return ApiResponse.ok(roleService.update(id, req));
    }

    @PatchMapping("/{id}/enabled")
    @RequireRoles.Admin
    @AuditLog(module = "ROLE", action = "ENABLED")
    @Operation(summary = "启/停角色")
    public ApiResponse<RoleListItem> setEnabled(@PathVariable Long id,
                                                @RequestBody(required = false) java.util.Map<String, Object> body) {
        Boolean enabled = body == null ? null : (Boolean) body.get("enabled");
        return ApiResponse.ok(roleService.setEnabled(id, enabled != null && enabled));
    }

    /** V4.12: 批量启停 (内置角色有在用用户时会跳过) */
    @PatchMapping("/bulk-enabled")
    @RequireRoles.Admin
    @AuditLog(module = "ROLE", action = "BULK_ENABLED", extractResourceId = false)
    @Operation(summary = "批量启/停角色")
    public ApiResponse<java.util.Map<String, Object>> bulkSetEnabled(
            @RequestBody @Valid BulkEnabledRequest req) {
        return ApiResponse.ok(roleService.bulkSetEnabled(req.ids(), req.enabled()));
    }

    @DeleteMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "ROLE", action = "DELETE", extractResourceId = false)
    @Operation(summary = "删除角色 (内置角色/在用角色 不可删)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok();
    }
}
