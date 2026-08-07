package com.company.zhiyu.module.menu;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.audit.AuditLog;
import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.common.security.RequireRoles;
import com.company.zhiyu.module.menu.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * L1-3 菜单管理 Controller
 *
 * 端点:
 *   GET    /api/menus                 列表 (?includeDisabled=true 含已停用)
 *   GET    /api/menus/parent-options  父菜单下拉 (排除自身)
 *   GET    /api/menus/{id}            详情
 *   POST   /api/menus                 新建 (PMO_ADMIN/ADMIN)
 *   PUT    /api/menus/{id}            更新 (PMO_ADMIN/ADMIN)
 *   PATCH  /api/menus/{id}/enabled    启停 (PMO_ADMIN/ADMIN)
 *   DELETE /api/menus/{id}            删除 (PMO_ADMIN/ADMIN, 内置不可删)
 */
@RestController
@RequestMapping("/menus")
@RequiredArgsConstructor
@Tag(name = "Menus Admin (L1-3)", description = "菜单管理 — 增删改查/启停/父级联动")
public class SysMenuAdminController {

    private final SysMenuService menuService;

    @GetMapping
    @RequireRoles.Read
    @Operation(summary = "菜单列表")
    public ApiResponse<List<SysMenuItem>> list(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(menuService.list(includeDisabled));
    }

    @GetMapping("/parent-options")
    @RequireRoles.Read
    @Operation(summary = "父菜单下拉 (排除自身)")
    public ApiResponse<List<SysMenuItem>> parentOptions(
            @RequestParam(required = false) Long excludeId) {
        return ApiResponse.ok(menuService.parentOptions(excludeId));
    }

    @GetMapping("/{id}")
    @RequireRoles.Read
    @Operation(summary = "菜单详情")
    public ApiResponse<SysMenuItem> get(@PathVariable Long id) {
        return ApiResponse.ok(menuService.get(id));
    }

    @PostMapping
    @RequireRoles.Admin
    @AuditLog(module = "MENU", action = "CREATE")
    @Operation(summary = "新建自定义菜单")
    public ApiResponse<SysMenuItem> create(@Valid @RequestBody SysMenuCreateRequest req) {
        return ApiResponse.ok(menuService.create(req));
    }

    @PutMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "MENU", action = "UPDATE")
    @Operation(summary = "更新菜单")
    public ApiResponse<SysMenuItem> update(@PathVariable Long id,
                                           @Valid @RequestBody SysMenuUpdateRequest req) {
        return ApiResponse.ok(menuService.update(id, req));
    }

    @PatchMapping("/{id}/enabled")
    @RequireRoles.Admin
    @AuditLog(module = "MENU", action = "ENABLED")
    @Operation(summary = "启/停菜单")
    public ApiResponse<SysMenuItem> setEnabled(@PathVariable Long id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        Boolean enabled = body == null ? null : (Boolean) body.get("enabled");
        return ApiResponse.ok(menuService.setEnabled(id, enabled != null && enabled));
    }

    /** V4.12: 批量启停 */
    @PatchMapping("/bulk-enabled")
    @RequireRoles.Admin
    @AuditLog(module = "MENU", action = "BULK_ENABLED", extractResourceId = false)
    @Operation(summary = "批量启/停菜单")
    public ApiResponse<Map<String, Object>> bulkSetEnabled(
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        Boolean enabled = (Boolean) body.get("enabled");
        if (ids == null || ids.isEmpty()) throw new BusinessException(422, "ids 不能为空");
        if (enabled == null) throw new BusinessException(422, "enabled 不能为空");
        int n = menuService.bulkSetEnabled(ids, enabled);
        return ApiResponse.ok(Map.of("affected", n));
    }

    @DeleteMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "MENU", action = "DELETE", extractResourceId = false)
    @Operation(summary = "删除菜单 (内置菜单/有子菜单的 不可删)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ApiResponse.ok();
    }
}