package com.hex.projectgovern.module.member;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.module.member.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目组成员 REST API
 *
 * <p>URL 设计:
 * <ul>
 *   <li>GET    /dict/member-roles                  角色字典(下拉用)</li>
 *   <li>GET    /projects/{projectId}/members       某项目的成员列表</li>
 *   <li>POST   /projects/{projectId}/members       添加成员</li>
 *   <li>PUT    /projects/{projectId}/members/{id}  更新成员</li>
 *   <li>DELETE /projects/{projectId}/members/{id}  删除成员(软删)</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Project Members", description = "项目组成员管理")
public class ProjectMemberController {

    private final ProjectMemberService service;

    /** 角色字典 */
    @GetMapping("/dict/member-roles")
    @Operation(summary = "项目成员角色字典(下拉用,按 sortOrder 升序)")
    public ApiResponse<List<MemberRoleResponse>> listRoles() {
        return ApiResponse.ok(service.listRoles());
    }

    @GetMapping("/projects/{projectId}/members")
    @Operation(summary = "某项目的成员列表")
    public ApiResponse<List<ProjectMemberResponse>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(service.listByProject(projectId));
    }

    @PostMapping("/projects/{projectId}/members")
    @AuditLog(module = "PROJECT", action = "CREATE")
    @Operation(summary = "添加项目成员")
    public ApiResponse<ProjectMemberResponse> add(@PathVariable Long projectId,
                                                  @Valid @RequestBody ProjectMemberRequest req) {
        return ApiResponse.ok(service.add(projectId, req));
    }

    @PutMapping("/projects/{projectId}/members/{id}")
    @AuditLog(module = "PROJECT", action = "UPDATE")
    @Operation(summary = "更新项目成员")
    public ApiResponse<ProjectMemberResponse> update(@PathVariable Long projectId,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody ProjectMemberRequest req) {
        // projectId 路径占位(一致性,未来做项目级权限校验时使用)
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/projects/{projectId}/members/{id}")
    @AuditLog(module = "PROJECT", action = "DELETE")
    @Operation(summary = "删除项目成员(软删)")
    public ApiResponse<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
