package com.hex.projectgovern.module.menu;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.common.security.RequireRoles;
import com.hex.projectgovern.module.menu.dto.*;
import com.hex.projectgovern.module.org.RoleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 角色 × 菜单 授权 Controller (V4.12 升级)
 *
 * 端点:
 *   GET    /api/role-menus/{roleId}                拿到该角色当前拥有的 menuId 列表
 *   PUT    /api/role-menus/{roleId}                全量替换授权 (Body: menuIds)
 *   POST   /api/role-menus/copy                    复制授权 (源角色 → 多个目标角色)
 *   GET    /api/role-menus/mine                    当前登录用户可见的菜单 code 列表 (前端过滤用)
 */
@RestController
@RequestMapping("/role-menus")
@RequiredArgsConstructor
@Tag(name = "Role-Menu Auth (L1-3)", description = "角色-菜单授权")
public class RoleMenuAdminController {

    private final RoleMenuRepository roleMenuRepo;
    private final RoleRepository roleRepo;
    private final SysMenuRepository menuRepo;

    /** 查询: 某角色当前拥有的 menuId */
    @GetMapping("/{roleId}")
    @RequireRoles.Admin
    @Operation(summary = "查询某角色已授权菜单 ID")
    public ApiResponse<List<Long>> listByRole(@PathVariable Long roleId) {
        if (!roleRepo.existsById(roleId)) {
            throw new BusinessException(404, "角色不存在: " + roleId);
        }
        return ApiResponse.ok(
                roleMenuRepo.findAllByRoleId(roleId).stream()
                        .map(RoleMenu::getMenuId)
                        .sorted()
                        .toList()
        );
    }

    /** 全量替换: 删除旧 + 插入新 (原子事务) */
    @PutMapping("/{roleId}")
    @RequireRoles.Admin
    @AuditLog(module = "ROLE_MENU", action = "ASSIGN")
    @Operation(summary = "全量替换某角色的菜单授权")
    @Transactional
    public ApiResponse<List<Long>> assign(@PathVariable Long roleId,
                                          @RequestBody RoleMenuAssignRequest req) {
        if (!roleRepo.existsById(roleId)) {
            throw new BusinessException(404, "角色不存在: " + roleId);
        }
        if (!Objects.equals(roleId, req.roleId())) {
            throw new BusinessException("路径参数 roleId 与 body.roleId 不一致");
        }

        // 1) 校验所有 menuId 都存在且启用, 自动跳过已停用
        List<Long> normalized = new ArrayList<>();
        if (req.menuIds() != null) {
            for (Long mid : req.menuIds()) {
                if (mid == null) continue;
                SysMenu m = menuRepo.findById(mid)
                        .orElseThrow(() -> new BusinessException(404, "菜单不存在: " + mid));
                if (!m.isEnabled()) continue;
                normalized.add(m.getId());
            }
            normalized = new ArrayList<>(new LinkedHashSet<>(normalized));
        }

        // 2) 删除原授权
        roleMenuRepo.deleteAllByRoleId(roleId);
        roleMenuRepo.flush();

        // 3) 插入新授权
        Instant now = Instant.now();
        for (Long mid : normalized) {
            RoleMenu rm = new RoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(mid);
            rm.setGrantedAt(now);
            roleMenuRepo.save(rm);
        }

        return ApiResponse.ok(normalized.stream().sorted().toList());
    }

    /** 当前登录用户可见的菜单 code (前端根据此过滤 App.vue 菜单) */
    @GetMapping("/mine")
    @RequireRoles.Read
    @Operation(summary = "当前登录用户可见的菜单 code 列表")
    public ApiResponse<List<String>> myVisibleMenuCodes(@RequestParam("roleIds") List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(roleMenuRepo.findEnabledMenuCodesByRoleIds(roleIds));
    }

    /** V4.12: 复制授权 — 源角色的菜单权限复制到 N 个目标角色 */
    @PostMapping("/copy")
    @RequireRoles.Admin
    @AuditLog(module = "ROLE_MENU", action = "COPY", extractResourceId = false)
    @Operation(summary = "复制授权 (源角色 → 多个目标)")
    public ApiResponse<Map<String, Object>> copy(@RequestBody @Valid CopyRoleMenuRequest req) {
        if (!roleRepo.existsById(req.sourceRoleId())) {
            throw new BusinessException(404, "源角色不存在: " + req.sourceRoleId());
        }
        for (Long tid : req.targetRoleIds()) {
            if (!roleRepo.existsById(tid)) {
                throw new BusinessException(404, "目标角色不存在: " + tid);
            }
        }
        boolean overwrite = Boolean.TRUE.equals(req.overwrite());

        // 源角色的 menuIds
        List<Long> sourceMenuIds = roleMenuRepo.findAllByRoleId(req.sourceRoleId()).stream()
                .map(RoleMenu::getMenuId).distinct().toList();

        int totalAffected = 0;
        Instant now = Instant.now();
        for (Long targetRoleId : req.targetRoleIds()) {
            if (Objects.equals(targetRoleId, req.sourceRoleId())) continue;
            if (overwrite) {
                roleMenuRepo.deleteAllByRoleId(targetRoleId);
                roleMenuRepo.flush();
            }
            // 已有的避免重复
            List<Long> existing = roleMenuRepo.findAllByRoleId(targetRoleId).stream()
                    .map(RoleMenu::getMenuId).toList();
            List<RoleMenu> toInsert = new ArrayList<>();
            for (Long mid : sourceMenuIds) {
                if (!existing.contains(mid)) {
                    RoleMenu rm = new RoleMenu();
                    rm.setRoleId(targetRoleId);
                    rm.setMenuId(mid);
                    rm.setGrantedAt(now);
                    toInsert.add(rm);
                }
            }
            if (!toInsert.isEmpty()) {
                roleMenuRepo.saveAll(toInsert);
                totalAffected += toInsert.size();
            }
        }
        return ApiResponse.ok(Map.of(
                "affected", totalAffected,
                "menuIdsCopied", sourceMenuIds,
                "overwrite", overwrite));
    }
}