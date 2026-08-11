package com.hex.projectgovern.module.dashboard.v5;

import com.hex.projectgovern.module.dashboard.DashboardService;
import com.hex.projectgovern.module.dashboard.quality.DataQualityService;
import com.hex.projectgovern.module.project.ProjectRepository;
import com.hex.projectgovern.module.risk.RiskRepository;
import com.hex.projectgovern.module.timesheet.TimesheetWeekRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * v5 角色仪表盘服务 (WP-M7-04).
 *
 * <p>8 角色:
 *  - PM: 项目进度 + WBS 任务
 *  - PMO: 多项目健康度 + 风险
 *  - EXEC: 战略视图 + 财务总览
 *  - DEPT_LEAD: 部门立项 + 审批待办
 *  - VIEWER: 只读 + 报表订阅
 *  - FINANCE: 跨项目成本 + 对账
 *  - HR: 人员负载 + 工时
 *  - AI_ADVISOR: 预测 + 建议
 *
 * <p>MVP: 统一返回 (角色 + KPI 集合 + 趋势 4 个数据点).
 */
@Service
@RequiredArgsConstructor
public class RoleDashboardService {

    private final DashboardService dashboardService;
    private final DataQualityService dataQualityService;
    private final ProjectRepository projectRepository;
    private final RiskRepository riskRepository;
    private final TimesheetWeekRepository timesheetRepo;

    /** 8 角色列表 */
    public static final List<String> SUPPORTED_ROLES = List.of(
        "PM", "PMO_ADMIN", "EXEC", "DEPT_LEAD", "VIEWER", "FINANCE", "HR", "AI_ADVISOR"
    );

    @Transactional(readOnly = true)
    public Map<String, Object> forRole(String roleCode, Long userId) {
        if (!SUPPORTED_ROLES.contains(roleCode)) {
            throw new IllegalArgumentException("Unsupported role: " + roleCode);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", roleCode);
        out.put("userId", userId);
        out.put("generatedAt", java.time.Instant.now().toString());
        out.put("kpis", dashboardService.kpis());
        out.put("statusDistribution", dashboardService.statusDistribution());
        out.put("healthDistribution", dashboardService.healthDistribution());

        // 角色特化
        switch (roleCode) {
            case "PM" -> out.put("myProjects", dashboardService.activeProjects().size());
            case "PMO_ADMIN" -> out.put("riskCount", riskRepository.findAll().size());
            case "EXEC" -> out.putAll(execView());
            case "DEPT_LEAD" -> out.put("pendingApprovals", timesheetRepo.count());
            case "VIEWER" -> out.put("subscriptionCount", 0);  // 留 v5 接
            case "FINANCE" -> out.putAll(financeView());
            case "HR" -> out.put("activeUserCount", projectRepository.findAllActive().size());
            case "AI_ADVISOR" -> out.put("dataQuality", dataQualityService.snapshot());
            default -> {}
        }
        return out;
    }

    private Map<String, Object> execView() {
        return Map.of("focusProjects", dashboardService.activeProjects().size());
    }

    private Map<String, Object> financeView() {
        return Map.of("financeDashboardPlaceholder", true);
    }
}
