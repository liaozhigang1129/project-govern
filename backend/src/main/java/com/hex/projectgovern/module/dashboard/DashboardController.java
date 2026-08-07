package com.hex.projectgovern.module.dashboard;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.security.RequireRoles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@RequireRoles.Read
@Tag(name = "Dashboard", description = "治理视角聚合统计")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/kpis")
    @Operation(summary = "4 项核心 KPI",
        description = "activeCount / overdueProjects / closedThisMonth / newInitiationsThisMonth")
    public ApiResponse<Map<String, Object>> kpis() { return ApiResponse.ok(dashboardService.kpis()); }

    @GetMapping("/status-distribution")
    @Operation(summary = "项目状态分布", description = "按 status.name 分组计数")
    public ApiResponse<Map<String, Long>> statusDist() { return ApiResponse.ok(dashboardService.statusDistribution()); }

    @GetMapping("/health-distribution")
    @Operation(summary = "项目健康度分布", description = "按 health.name 分组计数,跳过 health=null")
    public ApiResponse<Map<String, Long>> healthDist() { return ApiResponse.ok(dashboardService.healthDistribution()); }

    /** 健康项目列表(DTO,不暴露懒加载实体) */
    @GetMapping("/active-projects")
    @Transactional(readOnly = true)
    @Operation(summary = "项目卡片数据 (Dashboard 主表)")
    public ApiResponse<List<com.hex.projectgovern.module.dashboard.dto.ProjectCardDto>> activeProjects() {
        return ApiResponse.ok(dashboardService.activeProjects());
    }

    @GetMapping("/bu-distribution")
    @Operation(summary = "按业务单元(BU)分布", description = "每个 BU 的项目数量 + 平均进度")
    public ApiResponse<List<Map<String, Object>>> buDistribution() {
        return ApiResponse.ok(dashboardService.buDistribution());
    }

    @GetMapping("/pl-distribution")
    @Operation(summary = "按产品线(PL)分布", description = "每个 PL 的项目数量 + 平均进度 + 所属 BU")
    public ApiResponse<List<Map<String, Object>>> plDistribution() {
        return ApiResponse.ok(dashboardService.plDistribution());
    }
}
