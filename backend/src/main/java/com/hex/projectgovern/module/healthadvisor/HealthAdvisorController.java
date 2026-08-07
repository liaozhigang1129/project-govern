package com.hex.projectgovern.module.healthadvisor;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.security.RequireRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 健康度建议接口。
 *  - GET  /health-advisor/suggest/{id}      单项目 dry-run
 *  - POST /health-advisor/apply-all         全量跑批并回写(默认 dry-run)
 *  - GET  /health-advisor/health            模块自检
 */
@RestController
@RequestMapping("/health-advisor")
@RequiredArgsConstructor
@Tag(name = "HealthAdvisor", description = "项目健康度自动建议(延期 + 进度落后)")
public class HealthAdvisorController {

    private final HealthAdvisorService service;

    @GetMapping("/suggest/{id}")
    @RequireRoles.Read
    @Operation(summary = "单项目健康度建议(dry-run,不写库)")
    public ApiResponse<HealthSuggestion> suggest(@PathVariable Long id) {
        return ApiResponse.ok(service.suggestForProject(id));
    }

    @PostMapping("/apply-all")
    @RequireRoles.Admin
    @AuditLog(module = "HEALTH_ADVISOR", action = "RUN_BATCH", extractResourceId = false)
    @Operation(summary = "全量跑批",
        description = "默认 dry-run(只看不写);apply=true 才回写 project.health")
    public ApiResponse<Map<String, Object>> applyAll(
            @RequestParam(name = "apply", defaultValue = "false") boolean apply) {
        List<HealthSuggestion> list = service.runForAll(apply);
        long changed = list.stream()
                .filter(s -> s.getSuggestedCode() != null)
                .filter(s -> s.getCurrentCode() == null || !s.getCurrentCode().equals(s.getSuggestedCode()))
                .count();
        return ApiResponse.ok(Map.of(
                "total", list.size(),
                "changed", changed,
                "applied", apply,
                "items", list
        ));
    }
}
