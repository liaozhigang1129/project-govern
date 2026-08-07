package com.company.pmo.common.security;

import com.company.pmo.module.initiation.InitiationController;
import com.company.pmo.module.milestone.MilestoneController;
import com.company.pmo.module.project.ProjectController;
import com.company.pmo.module.dashboard.DashboardController;
import com.company.pmo.module.dict.DictController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequireRoles 注解元数据")
class RequireRolesTest {

    /**
     * 用 Spring 的 AnnotatedElementUtils 拿"合并后的"@PreAuthorize,
     * 这样 meta-annotation 也能穿透。
     */
    private static Map<String, String> collectAuthExpressions(Class<?>... classes) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Class<?> c : classes) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isSynthetic()) continue;
                PreAuthorize p = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
                if (p != null) {
                    String params = Arrays.stream(m.getParameterTypes())
                            .map(Class::getSimpleName).reduce((a, b) -> a + "," + b).orElse("");
                    out.put(c.getSimpleName() + "#" + m.getName() + "(" + params + ")", p.value());
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("1) RequireRoles 内部注解都带 @PreAuthorize")
    void metaHasPreAuthorize() {
        assertThat(AnnotatedElementUtils.findMergedAnnotation(RequireRoles.Read.class, PreAuthorize.class)).isNotNull();
        assertThat(AnnotatedElementUtils.findMergedAnnotation(RequireRoles.Operate.class, PreAuthorize.class)).isNotNull();
        assertThat(AnnotatedElementUtils.findMergedAnnotation(RequireRoles.Approve.class, PreAuthorize.class)).isNotNull();
        assertThat(AnnotatedElementUtils.findMergedAnnotation(RequireRoles.Admin.class, PreAuthorize.class)).isNotNull();
        assertThat(AnnotatedElementUtils.findMergedAnnotation(RequireRoles.Dict.class, PreAuthorize.class)).isNotNull();
    }

    @Test
    @DisplayName("2) InitiationController 6 个端点都标了 RequireRoles")
    void initiationAllMarked() {
        Map<String, String> m = collectAuthExpressions(InitiationController.class);
        assertThat(m).hasSize(6);
        // decide 必须是 Approve → 包含 DEPT_LEAD
        long approves = m.values().stream().filter(s -> s.contains("DEPT_LEAD")).count();
        assertThat(approves).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("3) MilestoneController 9 个端点 (V3.x 加上 update-plan-date/progress/completion-analysis/health/slippage-prediction)")
    void milestoneMixed() {
        Map<String, String> m = collectAuthExpressions(MilestoneController.class);
        assertThat(m).hasSize(9);
        // DELETE 必须是 Admin → 包含 PMO_ADMIN,且表达式短（避免和 Approve 混淆）
        boolean hasAdmin = m.values().stream().anyMatch(s -> s.contains("PMO_ADMIN") && s.length() < 60);
        assertThat(hasAdmin).isTrue();
    }

    @Test
    @DisplayName("4) ProjectController 3 个写端点(POST/PUT/DELETE)")
    void projectMixed() {
        Map<String, String> m = collectAuthExpressions(ProjectController.class);
        assertThat(m).hasSize(3);
    }

    @Test
    @DisplayName("5) DictController 类级别 @RequireRoles.Dict(包 VIEWER)")
    void dictClassLevel() {
        Class<DictController> c = DictController.class;
        PreAuthorize p = AnnotatedElementUtils.findMergedAnnotation(c, PreAuthorize.class);
        assertThat(p).isNotNull();
        assertThat(p.value()).contains("VIEWER");
    }

    @Test
    @DisplayName("6) DashboardController 类级别 @RequireRoles.Read = isAuthenticated()")
    void dashboardClassLevel() {
        Class<DashboardController> c = DashboardController.class;
        PreAuthorize p = AnnotatedElementUtils.findMergedAnnotation(c, PreAuthorize.class);
        assertThat(p).isNotNull();
        assertThat(p.value()).isEqualTo("isAuthenticated()");
    }
}
