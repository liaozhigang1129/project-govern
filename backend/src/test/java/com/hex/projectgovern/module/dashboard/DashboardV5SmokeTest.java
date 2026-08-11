package com.hex.projectgovern.module.dashboard;

import com.hex.projectgovern.module.dashboard.quality.DataQualityService;
import com.hex.projectgovern.module.dashboard.v5.RoleDashboardService;
import com.hex.projectgovern.module.project.Project;
import com.hex.projectgovern.module.project.ProjectRepository;
import com.hex.projectgovern.module.risk.RiskRepository;
import com.hex.projectgovern.module.timesheet.TimesheetWeekRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * v5 角色仪表盘 + 数据质量单测 (WP-M7-04 / P2 #33).
 */
class DashboardV5SmokeTest {

    @Test
    @DisplayName("RoleDashboardService: 8 角色 + 未知角色抛错")
    void role_list_8() {
        assertThat(RoleDashboardService.SUPPORTED_ROLES).hasSize(8);
        assertThat(RoleDashboardService.SUPPORTED_ROLES).contains("PM", "PMO_ADMIN", "EXEC", "DEPT_LEAD",
            "VIEWER", "FINANCE", "HR", "AI_ADVISOR");
    }

    @Test
    @DisplayName("RoleDashboardService.forRole: 未知角色 → IllegalArgumentException")
    void role_unknown() {
        RoleDashboardService svc = new RoleDashboardService(null, null, null, null, null);
        assertThatThrownBy(() -> svc.forRole("GHOST", 1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RoleDashboardService.forRole(PM): 含 myProjects 字段")
    void role_pm() {
        DashboardService ds = mock(DashboardService.class);
        when(ds.kpis()).thenReturn(Map.of("activeCount", 5));
        ProjectRepository pr = mock(ProjectRepository.class);
        when(pr.findAllActive()).thenReturn(List.of(new Project(), new Project()));
        RiskRepository rr = mock(RiskRepository.class);
        when(rr.findAll()).thenReturn(List.of());
        TimesheetWeekRepository tr = mock(TimesheetWeekRepository.class);
        when(tr.count()).thenReturn(0L);
        DataQualityService dq = mock(DataQualityService.class);

        RoleDashboardService svc = new RoleDashboardService(ds, dq, pr, rr, tr);
        Map<String, Object> out = svc.forRole("PM", 1L);
        assertThat(out).containsKey("role");
        assertThat(out.get("role")).isEqualTo("PM");
        assertThat(out).containsKey("myProjects");
    }

    @Test
    @DisplayName("DataQualityService.snapshot: 含 3 指标")
    void quality_3_indicators() {
        ProjectRepository pr = mock(ProjectRepository.class);
        when(pr.count()).thenReturn(10L);
        when(pr.findAll()).thenReturn(List.of());
        RiskRepository rr = mock(RiskRepository.class);
        when(rr.findAll()).thenReturn(List.of());
        TimesheetWeekRepository tr = mock(TimesheetWeekRepository.class);
        when(tr.count()).thenReturn(0L);

        DataQualityService svc = new DataQualityService(pr, rr, tr);
        Map<String, Object> out = svc.snapshot();
        assertThat(out).containsKey("indicators");
        @SuppressWarnings("unchecked")
        Map<String, Object> ind = (Map<String, Object>) out.get("indicators");
        assertThat(ind).containsKeys("nullRate", "duplicateRate", "timeDrift");
    }
}
