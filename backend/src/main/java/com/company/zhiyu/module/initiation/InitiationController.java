package com.company.zhiyu.module.initiation;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.audit.AuditLog;
import com.company.zhiyu.common.security.RequireRoles;
import com.company.zhiyu.module.org.AppUser;
import com.company.zhiyu.module.org.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/initiations")
@RequiredArgsConstructor
@Tag(name = "Initiations", description = "项目立项 + 3 级审批流转")
public class InitiationController {

    private final InitiationService initiationService;
    private final UserRepository userRepository;

    @GetMapping
    @RequireRoles.Read
    @Operation(summary = "立项列表 (JOIN FETCH status 防 LAZY)",
               description = "支持查询参数: keyword(编号/标题模糊搜索), statusCode(状态), currentStep(当前步骤), applicantId(申请人), departmentId(部门), startDate/endDate(提交时间范围)")
    public ApiResponse<List<ProjectInitiation>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String statusCode,
            @RequestParam(required = false) String currentStep,
            @RequestParam(required = false) Long applicantId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) java.time.LocalDate startDate,
            @RequestParam(required = false) java.time.LocalDate endDate) {
        return ApiResponse.ok(initiationService.listWithFilters(keyword, statusCode, currentStep, applicantId, departmentId, startDate, endDate));
    }

    @GetMapping("/{id}")
    @RequireRoles.Read
    @Operation(summary = "立项详情")
    public ApiResponse<ProjectInitiation> get(@PathVariable Long id) {
        return ApiResponse.ok(initiationService.get(id));
    }

    @GetMapping("/{id}/records")
    @RequireRoles.Read
    @Operation(summary = "审批记录流水 (按 decidedAt 升序)")
    public ApiResponse<List<ApprovalRecord>> records(@PathVariable Long id) {
        return ApiResponse.ok(initiationService.records(id));
    }

    @PostMapping("/{id}/resubmit")
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "RESUBMIT", extractResourceId = false)
    @Operation(summary = "补料后重新提交",
        description = "仅 SUPPLEMENT 状态可调;状态置回 PENDING,currentStep 不变(等当前审批人重审)。")
    public ApiResponse<Map<String, Object>> resubmit(@PathVariable Long id,
                                                     @AuthenticationPrincipal UserDetails ud) {
        AppUser user = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();
        ProjectInitiation i = initiationService.resubmit(id, user.getId());
        return ApiResponse.ok(Map.of(
                "id", i.getId(),
                "code", i.getCode(),
                "status", i.getStatus().getCode(),
                "statusName", i.getStatus().getName(),
                "currentStep", i.getCurrentStep() == null ? "" : i.getCurrentStep()
        ));
    }

    @PostMapping
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "CREATE")
    @Operation(summary = "提交立项 (状态置 PENDING, currentStep=DEPT_LEAD)")
    public ApiResponse<ProjectInitiation> submit(@RequestBody ProjectInitiation i,
                                                  @AuthenticationPrincipal UserDetails ud) {
        // 申请人以 JWT 实际用户为准,避免 body 漏传
        AppUser user = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();
        i.setApplicantId(user.getId());
        if (i.getDepartmentId() == null) {
            i.setDepartmentId(user.getDepartmentId());
        }
        return ApiResponse.ok(initiationService.submit(i));
    }

    /**
     * V4.19 增量更新立项字段 (Step 1 改合同金额 / Step 4 同步合同金额 / 备注等)
     * <p>仅更新 body 里非 null 的字段,避免覆盖其他字段
     */
    @PatchMapping("/{id}")
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "UPDATE_FIELDS", extractResourceId = false)
    @Operation(summary = "增量更新立项字段 (合同金额等)",
        description = "仅更新 body 里非 null 的字段;典型场景:Step 4 改合同金额后同步到立项表")
    public ApiResponse<ProjectInitiation> updateFields(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> patch,
            @AuthenticationPrincipal UserDetails ud) {
        AppUser user = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();
        return ApiResponse.ok(initiationService.updateFields(id, patch, user.getId()));
    }

    /**
     * 增量更新 SOW 贴文本(纯文本来源,无需上传文件)。
     * AI WBS 生成时会自动合并文件 + 贴文本,任意一个非空都能触发。
     */
    @PatchMapping("/{id}/sow-paste")
    @RequireRoles.Operate
    @AuditLog(module = "INITIATION", action = "UPDATE_SOW_PASTE", extractResourceId = false)
    @Operation(summary = "保存/更新 SOW 贴文本(Step 2 第二种 SOW 来源)",
        description = "request: {sowPasteText: string|null}; " +
                      "后端自动 trim,>50KB 截断;若为非空且无 SOW 文件则置 sowReceived=true")
    public ApiResponse<Map<String, Object>> updateSowPaste(
            @PathVariable Long id,
            @RequestBody SowPasteRequest body,
            @AuthenticationPrincipal UserDetails ud) {
        AppUser user = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();
        return ApiResponse.ok(initiationService.updateSowPaste(id, body.sowPasteText(), user.getId()));
    }

    public record SowPasteRequest(String sowPasteText) {}

    @PostMapping("/{id}/decide")
    @RequireRoles.Approve
    @AuditLog(module = "INITIATION", action = "APPROVE", extractResourceId = false)
    @Operation(summary = "审批决定",
        description = "decision ∈ {APPROVED, REJECTED, SUPPLEMENT}。3 级后自动建项目。")
    public ApiResponse<Map<String, Object>> decide(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserDetails ud,
                                                  @RequestBody InitiationService.ApprovalDecision d) {
        AppUser user = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();
        ProjectInitiation i = initiationService.decide(id, user.getId(), d);
        return ApiResponse.ok(Map.of(
                "id", i.getId(),
                "code", i.getCode(),
                "status", i.getStatus().getCode(),
                "statusName", i.getStatus().getName(),
                "currentStep", i.getCurrentStep() == null ? "" : i.getCurrentStep(),
                "projectId", i.getProjectId() == null ? -1 : i.getProjectId()
        ));
    }

    /**
     * 软删除立项。
     * 权限: 申请人本人 或 管理员。
     * 阻断: 已关联项目 / 已 EXEC_APPROVED 终审通过。
     */
    @DeleteMapping("/{id}")
    @RequireRoles.Read
    @AuditLog(module = "INITIATION", action = "DELETE")
    @Operation(summary = "软删除立项 (申请人本人或管理员)",
        description = "已关联项目 (project_id != null) → 400 拒绝;已 EXEC_APPROVED → 400 拒绝;其余状态允许。")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal UserDetails ud) {
        AppUser user = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();
        initiationService.softDelete(id, user.getId());
        return ApiResponse.ok(null);
    }
}
