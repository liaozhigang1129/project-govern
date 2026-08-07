package com.hex.projectgovern.module.dict;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.security.RequireRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
@RequireRoles.Dict
@Tag(name = "Dictionaries", description = "字典查询 (项目类型/状态/健康度/审批步骤等)")
public class DictController {

    private final ProjectTypeRepository projectTypeRepo;
    private final ProjectStatusRepository projectStatusRepo;
    private final HealthLevelRepository healthLevelRepo;
    private final InitiationStatusRepository initiationStatusRepo;
    private final MilestoneStatusRepository milestoneStatusRepo;
    private final ApprovalStepRepository approvalStepRepo;
    private final ProjectLevelRepository projectLevelRepo;

    @GetMapping("/project-types")
    @Operation(summary = "项目类型字典")
    public ApiResponse<?> projectTypes() { return ApiResponse.ok(projectTypeRepo.findAll()); }

    @GetMapping("/project-levels")
    @Operation(summary = "项目级别字典 (S/A/B/C)")
    public ApiResponse<?> projectLevels() { return ApiResponse.ok(projectLevelRepo.findAllByOrderBySortOrderAsc()); }

    @GetMapping("/project-statuses")
    @Operation(summary = "项目状态字典")
    public ApiResponse<?> projectStatuses() { return ApiResponse.ok(projectStatusRepo.findAll()); }

    @GetMapping("/health-levels")
    @Operation(summary = "健康度字典")
    public ApiResponse<?> healthLevels() { return ApiResponse.ok(healthLevelRepo.findAll()); }

    @GetMapping("/initiation-statuses")
    @Operation(summary = "立项状态字典")
    public ApiResponse<?> initiationStatuses() { return ApiResponse.ok(initiationStatusRepo.findAll()); }

    @GetMapping("/milestone-statuses")
    @Operation(summary = "里程碑状态字典")
    public ApiResponse<?> milestoneStatuses() { return ApiResponse.ok(milestoneStatusRepo.findAll()); }

    @GetMapping("/approval-steps")
    @Operation(summary = "审批步骤字典")
    public ApiResponse<?> approvalSteps() { return ApiResponse.ok(approvalStepRepo.findAll()); }
}
