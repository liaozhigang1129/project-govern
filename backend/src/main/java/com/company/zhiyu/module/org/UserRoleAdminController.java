package com.company.zhiyu.module.org;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.audit.AuditLog;
import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.common.security.RequireRoles;
import com.company.zhiyu.module.org.dto.UserRoleAssignRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户-角色多对多管理 Controller (V4.13)
 * - 独立接口, 让前端"为某用户配置角色"有干净的入口
 * - GET /api/users/{userId}/roles            拿该用户当前全部角色详情
 * - PUT /api/users/{userId}/roles            全量替换角色分配 (角色 ID 列表)
 * - GET /api/role-menus/assignable           给用户分配的"可选角色"简表
 *
 * 设计要点:
 *   1) 主角色 (primary_role_id) 必须保留在 user_role_assignments 表中
 *      (否则 detail 找不到, /me/roles 也会漏)
 *   2) 全部用户角色统一为"主 + 兼任",前端只需编辑一个角色列表
 *   3) 改完自动选第一个为 primary_role (若没传 primary)
 *   4) 自保护: 不能把自己降级 / 不能降光所有 PMO_ADMIN
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User-Role Assign (L1-2)", description = "用户-角色分配 — 配角色后,登录用户按此菜单权限展示资源")
public class UserRoleAdminController {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;

    // ============================================================
    //  1) 查某用户的全部角色 (前端权限回显用)
    // ============================================================
    @GetMapping("/users/{userId}/roles")
    @RequireRoles.Read
    @Operation(summary = "查询某用户的全部角色详情(含主角色标识)")
    public ApiResponse<Map<String, Object>> listByUser(@PathVariable Long userId) {
        AppUser u = userRepo.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(404, "USER.NOT_FOUND"));
        List<Long> roleIds = userRoleRepo.findRoleIdsByUserId(userId);
        Map<Long, Role> byId = roleRepo.findAllById(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, r -> r));

        List<Map<String, Object>> roleList = roleIds.stream().map(rid -> {
            Role r = byId.get(rid);
            if (r == null) return null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("code", r.getCode());
            m.put("name", r.getName());
            m.put("enabled", r.isEnabled());
            m.put("builtin", r.isBuiltIn());
            m.put("primary", Objects.equals(r.getId(),
                    u.getPrimaryRole() == null ? -1L : u.getPrimaryRole().getId()));
            return m;
        }).filter(Objects::nonNull).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("primaryRoleId", u.getPrimaryRole() == null ? null : u.getPrimaryRole().getId());
        data.put("primaryRoleCode", u.getPrimaryRole() == null ? null : u.getPrimaryRole().getCode());
        data.put("roles", roleList);
        return ApiResponse.ok(data);
    }

    // ============================================================
    //  2) 全量替换: PUT /api/users/{userId}/roles
    //      Body: { "roleIds":[1,2,3], "primaryRoleId":1 }
    //      roleIds 必须包含主角色 (或主角色自动选第一个)
    // ============================================================
    @PutMapping("/users/{userId}/roles")
    @RequireRoles.Admin
    @AuditLog(module = "USER_ROLE", action = "ASSIGN", extractResourceId = false)
    @Operation(summary = "全量替换某用户的角色分配(含主角色)")
    @Transactional
    public ApiResponse<Map<String, Object>> assign(@PathVariable Long userId,
                                                  @RequestBody @Valid UserRoleAssignRequest req) {
        if (!Objects.equals(userId, req.userId())) {
            throw new BusinessException(400, "路径 userId 与 body.userId 不一致");
        }
        AppUser u = userRepo.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(404, "USER.NOT_FOUND"));

        // (a) 校验: roleIds 全部存在且启用
        List<Long> ids = new ArrayList<>();
        if (req.roleIds() != null) {
            for (Long rid : req.roleIds()) {
                if (rid == null) continue;
                Role r = roleRepo.findById(rid)
                        .orElseThrow(() -> new BusinessException(404, "ROLE.NOT_FOUND: " + rid));
                if (!r.isEnabled()) continue;     // 自动跳过已停用角色
                ids.add(r.getId());
            }
        }
        ids = new ArrayList<>(new LinkedHashSet<>(ids));  // 去重保序

        // (b) 必须至少 1 个角色
        if (ids.isEmpty()) {
            throw new BusinessException(422, "USER_ROLE.EMPTY: 至少分配一个角色");
        }

        // (c) 自保护: 不能把自己降级
        if (Objects.equals(u.getId(), currentUserId())) {
            boolean adminStillThere = ids.stream().anyMatch(rid -> {
                try {
                    return "PMO_ADMIN".equals(roleRepo.findById(rid).orElseThrow().getCode());
                } catch (Exception e) { return false; }
            });
            if (!adminStillThere) {
                throw new BusinessException(409, "USER.SELF_DEMOTE: 不能把自己降为非 PMO_ADMIN");
            }
        }

        // (d) 自保护: 不能降光所有 PMO_ADMIN
        boolean isAdmin = u.getPrimaryRole() != null
                && "PMO_ADMIN".equals(u.getPrimaryRole().getCode());
        boolean willLoseAdmin = isAdmin && ids.stream().noneMatch(rid -> {
            try {
                return "PMO_ADMIN".equals(roleRepo.findById(rid).orElseThrow().getCode());
            } catch (Exception e) { return false; }
        });
        if (willLoseAdmin
                && userRepo.countByPrimaryRoleCodeAndEnabledAndDeletedFalse("PMO_ADMIN", true) <= 1) {
            throw new BusinessException(409, "USER.LAST_ADMIN: 不能降级最后一名 PMO_ADMIN");
        }

        // (e) 主角色 = ids 的第一个(也可以是 body 里的 primaryRoleId, 但我们简化: 取第一个)
        Long primaryId = ids.get(0);
        Role primary = roleRepo.findById(primaryId).orElseThrow();

        // (f) 删除旧 user_role, 插入新
        userRoleRepo.deleteAllByUserId(userId);
        userRoleRepo.flush();
        Instant now = Instant.now();
        Long grantedBy = currentUserId() == null ? 1L : currentUserId();
        List<UserRole> toInsert = ids.stream().map(rid -> UserRole.builder()
                .userId(userId).roleId(rid)
                .grantedAt(now).grantedBy(grantedBy).build()).toList();
        userRoleRepo.saveAll(toInsert);

        // (g) 更新 AppUser.primaryRoleId
        u.setPrimaryRole(primary);
        userRepo.save(u);

        log.info("USER_ROLE.ASSIGN userId={} roles={} primary={} by={}",
                userId, ids, primary.getCode(), grantedBy);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("primaryRoleId", primary.getId());
        data.put("primaryRoleCode", primary.getCode());
        data.put("roleIds", ids);
        return ApiResponse.ok(data);
    }

    // ============================================================
    //  3) 简表: 给用户分配用的"可选角色"
    // ============================================================
    @GetMapping("/role-menus/assignable")
    @RequireRoles.Read
    @Operation(summary = "可分配的角色简表(给用户配置角色时用, 已过滤停用)")
    public ApiResponse<?> assignable() {
        List<Map<String, Object>> data = new ArrayList<>();
        roleRepo.findAll().stream()
                .filter(Role::isEnabled)
                .sorted(Comparator.comparing(Role::getCode))
                .forEach(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("code", r.getCode());
                    m.put("name", r.getName());
                    m.put("builtin", r.isBuiltIn());
                    data.add(m);
                });
        return ApiResponse.ok(data);
    }

    // ============================================================
    //  4) V4.16 批量角色分配
    //  POST /api/users/batch/roles
    //  Body: { userIds:[1,2,3], roleIds:[5,6], mode:"REPLACE|ADD|REMOVE" }
    // ============================================================
    @PostMapping(value = "/users/batch/roles", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE, produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @RequireRoles.Admin
    @AuditLog(module = "USER_ROLE", action = "BATCH_ASSIGN", extractResourceId = false)
    @Operation(summary = "批量给多个用户应用同一组角色")
    @Transactional
    public ApiResponse<Map<String, Object>> batchAssign(@RequestBody @Valid com.company.zhiyu.module.org.dto.BatchUserRoleAssignRequest req) {
        if (req.userIds() == null || req.userIds().isEmpty()) {
            throw new BusinessException(400, "userIds 不能为空");
        }
        if (req.roleIds() == null || req.roleIds().isEmpty()) {
            throw new BusinessException(400, "roleIds 不能为空");
        }
        String mode = (req.mode() == null || req.mode().isBlank()) ? "REPLACE" : req.mode().toUpperCase();
        if (!Set.of("REPLACE", "ADD", "REMOVE").contains(mode)) {
            throw new BusinessException(400, "mode 必须是 REPLACE | ADD | REMOVE");
        }

        // 校验角色存在 + 过滤停用
        List<Long> roleIds = new ArrayList<>();
        for (Long rid : req.roleIds()) {
            if (rid == null) continue;
            Role r = roleRepo.findById(rid)
                    .orElseThrow(() -> new BusinessException(404, "ROLE.NOT_FOUND: " + rid));
            if (r.isEnabled()) roleIds.add(r.getId());
        }
        if (roleIds.isEmpty()) {
            throw new BusinessException(422, "选中的角色全部已停用");
        }
        roleIds = new ArrayList<>(new LinkedHashSet<>(roleIds));

        // 校验目标用户存在
        List<AppUser> users = userRepo.findAllById(req.userIds());
        if (users.size() != req.userIds().size()) {
            Set<Long> found = users.stream().map(AppUser::getId).collect(Collectors.toSet());
            List<Long> missing = req.userIds().stream().filter(id -> !found.contains(id)).toList();
            throw new BusinessException(404, "USER.NOT_FOUND: " + missing);
        }

        Instant now = Instant.now();
        Long grantedBy = currentUserId() == null ? 1L : currentUserId();
        int success = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        for (AppUser u : users) {
            try {
                // 自保护: 不能把自己降级
                if (Objects.equals(u.getId(), currentUserId())
                        && !roleIds.isEmpty()
                        && roleIds.stream().noneMatch(rid -> {
                            try {
                                return "PMO_ADMIN".equals(roleRepo.findById(rid).orElseThrow().getCode());
                            } catch (Exception e) { return false; }
                        })) {
                    errors.add("user " + u.getId() + " (自己): 降级为非 PMO_ADMIN");
                    failed++;
                    continue;
                }
                // 自保护: 不能降光所有 PMO_ADMIN
                boolean isAdmin = u.getPrimaryRole() != null
                        && "PMO_ADMIN".equals(u.getPrimaryRole().getCode());
                boolean willLoseAdmin = isAdmin && roleIds.stream().noneMatch(rid -> {
                    try {
                        return "PMO_ADMIN".equals(roleRepo.findById(rid).orElseThrow().getCode());
                    } catch (Exception e) { return false; }
                });
                if (willLoseAdmin
                        && userRepo.countByPrimaryRoleCodeAndEnabledAndDeletedFalse("PMO_ADMIN", true) <= 1) {
                    errors.add("user " + u.getId() + ": 最后一名 PMO_ADMIN");
                    failed++;
                    continue;
                }

                // 当前角色
                List<Long> currentRoleIds = userRoleRepo.findRoleIdsByUserId(u.getId());
                List<Long> targetRoleIds;
                switch (mode) {
                    case "REPLACE" -> targetRoleIds = new ArrayList<>(roleIds);
                    case "ADD" -> {
                        targetRoleIds = new ArrayList<>(new LinkedHashSet<>(currentRoleIds));
                        for (Long rid : roleIds) {
                            if (!targetRoleIds.contains(rid)) targetRoleIds.add(rid);
                        }
                    }
                    case "REMOVE" -> {
                        final List<Long> finalRoleIds = roleIds;
                        targetRoleIds = currentRoleIds.stream()
                                .filter(rid -> !finalRoleIds.contains(rid))
                                .collect(Collectors.toList());
                    }
                    default -> targetRoleIds = new ArrayList<>(roleIds);
                }
                if (targetRoleIds.isEmpty()) {
                    errors.add("user " + u.getId() + ": 操作后无角色, 跳过");
                    failed++;
                    continue;
                }
                // 主角色: 保留现有主角色, 若被移除则取第一个
                Long primaryId = u.getPrimaryRole() == null ? null : u.getPrimaryRole().getId();
                if (primaryId == null || !targetRoleIds.contains(primaryId)) {
                    primaryId = targetRoleIds.get(0);
                }
                Role primary = roleRepo.findById(primaryId).orElseThrow();

                // 写库
                userRoleRepo.deleteAllByUserId(u.getId());
                userRoleRepo.flush();
                List<UserRole> toInsert = targetRoleIds.stream()
                        .map(rid -> UserRole.builder()
                                .userId(u.getId()).roleId(rid)
                                .grantedAt(now).grantedBy(grantedBy).build()).toList();
                userRoleRepo.saveAll(toInsert);
                u.setPrimaryRole(primary);
                userRepo.save(u);
                success++;
            } catch (Exception e) {
                log.warn("USER_ROLE.BATCH_ASSIGN failed for user {}: {}", u.getId(), e.getMessage());
                errors.add("user " + u.getId() + ": " + e.getMessage());
                failed++;
            }
        }

        log.info("USER_ROLE.BATCH_ASSIGN mode={} userIds={} roleIds={} success={} failed={} by={}",
                mode, req.userIds(), roleIds, success, failed, grantedBy);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", mode);
        data.put("roleIds", roleIds);
        data.put("totalRequested", req.userIds().size());
        data.put("success", success);
        data.put("failed", failed);
        data.put("errors", errors);
        return ApiResponse.ok(data);
    }

    private Long currentUserId() {
        try {
            var su = org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication();
            if (su == null || !su.isAuthenticated()) return null;
            return userRepo.findByUsernameAndDeletedFalse(su.getName()).map(AppUser::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}