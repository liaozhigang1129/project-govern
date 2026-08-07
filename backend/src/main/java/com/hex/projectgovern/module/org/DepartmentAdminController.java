package com.hex.projectgovern.module.org;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.common.security.RequireRoles;
import com.hex.projectgovern.module.org.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * L1-3 部门管理 Controller
 *
 * 端点:
 *   GET    /api/departments/tree       树状全量
 *   GET    /api/departments/options    简表 (下拉, 仅启用)
 *   GET    /api/departments/{id}       单个
 *   POST   /api/departments            新建 (PMO_ADMIN/ADMIN)
 *   PUT    /api/departments/{id}       更新 (PMO_ADMIN/ADMIN)
 *   PATCH  /api/departments/{id}/enabled  启停
 *   DELETE /api/departments/{id}       删除 (无子部门&无用户)
 */
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
@Tag(name = "Departments Admin (L1-3)", description = "部门管理 — 树状 CRUD / 启停 / 下拉")
public class DepartmentAdminController {

    private final DepartmentService deptService;
    private final DepartmentRepository deptRepo;
    private final UserService userService;
    private final UserRepository userRepo;

    // ============================================================
    //  读
    // ============================================================
    @GetMapping("/tree")
    @RequireRoles.Read
    @Operation(summary = "部门树")
    public ApiResponse<List<DepartmentNode>> tree() {
        return ApiResponse.ok(deptService.tree());
    }

    @GetMapping("/options")
    @RequireRoles.Read
    @Operation(summary = "部门简表 (下拉用)")
    public ApiResponse<List<DepartmentOption>> options() {
        return ApiResponse.ok(deptService.options());
    }

    @GetMapping("/{id}")
    @RequireRoles.Read
    @Operation(summary = "部门详情")
    public ApiResponse<DepartmentNode> get(@PathVariable Long id) {
        return ApiResponse.ok(deptService.get(id));
    }

    // ============================================================
    //  写
    // ============================================================
    @PostMapping
    @RequireRoles.Admin
    @AuditLog(module = "DEPT", action = "CREATE")
    @Operation(summary = "新建部门")
    public ApiResponse<DepartmentNode> create(@Valid @RequestBody DepartmentCreateRequest req) {
        return ApiResponse.ok(deptService.create(req));
    }

    @PutMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "DEPT", action = "UPDATE")
    @Operation(summary = "更新部门")
    public ApiResponse<DepartmentNode> update(@PathVariable Long id,
                                              @Valid @RequestBody DepartmentUpdateRequest req) {
        return ApiResponse.ok(deptService.update(id, req));
    }

    @PatchMapping("/{id}/enabled")
    @RequireRoles.Admin
    @AuditLog(module = "DEPT", action = "ENABLED")
    @Operation(summary = "启/停部门")
    public ApiResponse<DepartmentNode> setEnabled(@PathVariable Long id,
                                                  @RequestBody(required = false) java.util.Map<String, Object> body) {
        Boolean enabled = body == null ? null : (Boolean) body.get("enabled");
        return ApiResponse.ok(deptService.setEnabled(id, enabled != null && enabled));
    }

    /** V4.12: 批量启停 */
    @PatchMapping("/bulk-enabled")
    @RequireRoles.Admin
    @AuditLog(module = "DEPT", action = "BULK_ENABLED", extractResourceId = false)
    @Operation(summary = "批量启/停部门")
    public ApiResponse<java.util.Map<String, Object>> bulkSetEnabled(
            @RequestBody @Valid BulkEnabledRequest req) {
        int n = deptService.bulkSetEnabled(req.ids(), req.enabled());
        return ApiResponse.ok(java.util.Map.of("affected", n));
    }

    @DeleteMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "DEPT", action = "DELETE", extractResourceId = false)
    @Operation(summary = "删除部门 (软删, 无子部门&无用户)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return ApiResponse.ok();
    }

    /** V4.12: 全树(含停用), 给前端选择器/导入用 */
    @GetMapping("/tree-all")
    @RequireRoles.Read
    @Operation(summary = "部门全树 (含停用)")
    public ApiResponse<List<DepartmentNode>> treeAll() {
        return ApiResponse.ok(deptService.treeIncludingDisabled());
    }

    // ============================================================
    //  V4.14: 层级 / 子部门 / 缺失部门用户
    // ============================================================

    /**
     * V4.14: 查某部门及所有子部门 (基于 tree_path 前缀)
     * 用法: GET /api/departments/{id}/descendants
     */
    @GetMapping("/{id}/descendants")
    @RequireRoles.Read
    @Operation(summary = "部门 + 所有子部门 (V4.14)")
    public ApiResponse<List<DepartmentNode>> descendants(@PathVariable Long id) {
        List<Department> depts = deptService.findDescendants(id);
        // 找每个部门的父名 (用 stream 一次扫)
        return ApiResponse.ok(depts.stream()
                .map(d -> {
                    String parentName = depts.stream()
                            .filter(p -> p.getId().equals(d.getParentId()))
                            .findFirst()
                            .map(Department::getName)
                            .orElse(null);
                    return DepartmentNode.from(d, deptRepo.countUsers(d.getId()), parentName);
                })
                .toList());
    }

    // ============================================================
    //  V4.14: 用户-部门分配 (拖拽/手动用)
    // ============================================================

    /** 单个用户补部门 */
    @PutMapping("/users/{userId}/department")
    @RequireRoles.Admin
    @Operation(summary = "为用户设置部门 (V4.14 拖拽/手动)")
    public ApiResponse<AssignUserDeptResponse> assignUserDepartment(
            @PathVariable Long userId,
            @RequestParam Long departmentId) {
        AppUser u = userService.updateDepartment(userId, departmentId);
        Department d = deptRepo.findByIdAndDeletedFalse(departmentId)
                .orElseThrow(() -> new BusinessException(404, "DEPT.NOT_FOUND"));
        return ApiResponse.ok(new AssignUserDeptResponse(userId, d.getId(), d.getName(),
                java.time.Instant.now().toString()));
    }

    /** 批量分配部门 */
    @PostMapping("/users/bulk-assign")
    @RequireRoles.Admin
    @Operation(summary = "批量为用户设置部门 (V4.14)")
    public ApiResponse<BulkAssignResponse> bulkAssignUsers(@RequestBody BulkAssignRequest req) {
        if (req == null || req.userIds() == null || req.userIds().isEmpty()) {
            throw new BusinessException(400, "USER_IDS.EMPTY");
        }
        if (req.departmentId() == null) {
            throw new BusinessException(400, "DEPT_ID.NULL");
        }
        Department d = deptRepo.findByIdAndDeletedFalse(req.departmentId())
                .orElseThrow(() -> new BusinessException(404, "DEPT.NOT_FOUND"));
        int updated = userService.bulkSetDepartment(req.userIds(), req.departmentId());
        return ApiResponse.ok(new BulkAssignResponse(updated, d.getId(), d.getName(),
                java.time.Instant.now().toString()));
    }

    /** 缺失部门的用户 (V4.14) */
    @GetMapping("/users/missing")
    @RequireRoles.Read
    @Operation(summary = "未分配部门的用户列表 (V4.14)")
    public ApiResponse<Page<UserListItem>> usersWithoutDepartment(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 简化: 走 service 拿到 page, 再做 keyword 过滤
        Page<UserListItem> base = userService.findUsersWithoutDepartment(page, Math.min(size, 100));
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            List<UserListItem> filtered = base.getContent().stream()
                    .filter(u -> (u.username() != null && u.username().toLowerCase().contains(kw))
                              || (u.fullName() != null && u.fullName().toLowerCase().contains(kw))
                              || (u.email() != null && u.email().toLowerCase().contains(kw)))
                    .toList();
            return ApiResponse.ok(new PageImpl<>(filtered, base.getPageable(), filtered.size()));
        }
        return ApiResponse.ok(base);
    }

    // --- V4.14 入参/出参 DTOs ---
    public record AssignUserDeptResponse(
            Long userId,
            Long departmentId,
            String departmentName,
            String updatedAt
    ) {}
    public record BulkAssignRequest(
            java.util.List<Long> userIds,
            Long departmentId
    ) {}
    public record BulkAssignResponse(
            int updated,
            Long departmentId,
            String departmentName,
            String updatedAt
    ) {}
}
