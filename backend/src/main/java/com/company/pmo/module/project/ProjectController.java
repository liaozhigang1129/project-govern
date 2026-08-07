package com.company.pmo.module.project;

import com.company.pmo.common.api.ApiResponse;
import com.company.pmo.common.audit.AuditLog;
import com.company.pmo.common.security.RequireRoles;
import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.dashboard.dto.ProjectCardDto;
import com.company.pmo.module.dict.*;
import com.company.pmo.module.milestone.MilestoneService;
import com.company.pmo.module.milestone.dto.MilestoneResponse;
import com.company.pmo.module.org.AppUser;
import com.company.pmo.module.org.UserRepository;
import com.company.pmo.module.project.dto.ProjectCreateRequest;
import com.company.pmo.module.project.dto.ProjectDetailResponse;
import com.company.pmo.module.project.dto.ProjectOverviewResponse;
import com.company.pmo.module.project.dto.ProjectUpdateRequest;
import com.company.pmo.module.project.dto.ProjectQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "项目主数据 CRUD")
public class ProjectController {

    private final ProjectService projectService;
    private final MilestoneService milestoneService;
    private final BusinessUnitRepository buRepo;
    private final ProductLineRepository plRepo;
    private final RelatedProductRepository rpRepo;
    private final UserRepository userRepo;

    @GetMapping
    @Operation(summary = "项目列表 (多条件过滤: BU/PL/PM/起止日期/关键字)")
    public ApiResponse<List<ProjectCardDto>> list(ProjectQuery q) {
        return ApiResponse.ok(projectService.searchCards(q));
    }

    /**
     * 项目详情 — DTO 形式,避免 LAZY 反序列化错误
     */
    @GetMapping("/{id}")
    @Operation(summary = "项目详情 (DTO,事务内完成字典反查)")
    public ApiResponse<ProjectDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(projectService.getDetail(id));
    }

    /**
     * 项目详情页聚合 — 一次拿到 详情 + 里程碑 + 加权进度
     * <p>替代前端 onMounted 发 3 个请求的瀑布
     */
    @GetMapping("/{id}/overview")
    @Operation(summary = "项目详情页聚合(项目 + 里程碑 + 进度)")
    public ApiResponse<ProjectOverviewResponse> overview(@PathVariable Long id) {
        ProjectDetailResponse detail = projectService.getDetail(id);
        List<MilestoneResponse> milestones = milestoneService.listByProject(id).stream()
                .map(MilestoneResponse::from).toList();
        int progressPct = milestoneService.computeWeightedProgress(id);
        return ApiResponse.ok(new ProjectOverviewResponse(detail, milestones, progressPct));
    }

    /**
     * 新建项目 — 接收 code 字符串,不暴露字典 id
     */
    @PostMapping
    @RequireRoles.Operate
    @AuditLog(module = "PROJECT", action = "CREATE")
    @Operation(summary = "创建项目")
    public ApiResponse<ProjectDetailResponse> create(@Valid @RequestBody ProjectCreateRequest req) {
        return ApiResponse.ok(projectService.createFromRequest(req));
    }

    /**
     * 局部更新 — code 不可改
     */
    @PutMapping("/{id}")
    @RequireRoles.Operate
    @AuditLog(module = "PROJECT", action = "UPDATE", extractResourceId = false)
    @Operation(summary = "局部更新项目 (DTO)")
    public ApiResponse<ProjectDetailResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProjectUpdateRequest req) {
        return ApiResponse.ok(projectService.updateFromRequest(id, req));
    }

    @DeleteMapping("/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "PROJECT", action = "DELETE")
    @Operation(summary = "软删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.softDelete(id);
        return ApiResponse.ok(null);
    }
}
