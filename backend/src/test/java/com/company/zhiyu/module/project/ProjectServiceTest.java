package com.company.zhiyu.module.project;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.module.dict.*;
import com.company.zhiyu.module.member.ProjectMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * ProjectService 业务规则测试:
 *  - create: 重名拒绝 (existsByCodeAndDeletedFalse)
 *  - create: 必填 type/status 的 ID 必须存在
 *  - get: 软删/不存在都返 404
 *  - update: 局部更新 (name/code 不动,其他字段可选)
 *  - softDelete: 软删后再 get 抛 404
 */
@DataJpaTest
@AutoConfigureTestDatabase
@Import({ProjectService.class, ProjectServiceTest.MockConfig.class})
@ActiveProfiles("test")
class ProjectServiceTest {

    /** ProjectService 依赖 ProjectMemberService,本测试只验证项目主数据,提供空实现 */
    @TestConfiguration
    static class MockConfig {
        @Bean
        ProjectMemberService projectMemberService() {
            return org.mockito.Mockito.mock(ProjectMemberService.class);
        }
    }

    @Autowired ProjectService projectService;
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectTypeRepository typeRepo;
    @Autowired ProjectStatusRepository statusRepo;
    @Autowired HealthLevelRepository healthRepo;

    private Long typeId, statusId, healthId;

    @BeforeEach
    void seedDicts() {
        ProjectType t = new ProjectType();
        t.setCode("DELIVERY"); t.setName("客户交付");
        typeId = typeRepo.save(t).getId();

        ProjectStatus s = new ProjectStatus();
        s.setCode("ACTIVE"); s.setName("执行中"); s.setTerminal(false);
        statusId = statusRepo.save(s).getId();

        HealthLevel h = new HealthLevel();
        h.setCode("GREEN"); h.setName("正常"); h.setColorHex("#67C23A");
        healthId = healthRepo.save(h).getId();
    }

    private Project mkProject(String code) {
        Project p = new Project();
        p.setCode(code);
        p.setName("项目 " + code);
        p.setPlanStartDate(LocalDate.now());
        p.setPlanEndDate(LocalDate.now().plusMonths(3));
        p.setBudgetEstimate(new BigDecimal("100000.00"));
        p.setType(refType(typeId));
        p.setStatus(refStatus(statusId));
        p.setHealth(refHealth(healthId));
        return p;
    }

    private ProjectType refType(Long id) { ProjectType x = new ProjectType(); x.setId(id); return x; }
    private ProjectStatus refStatus(Long id) { ProjectStatus x = new ProjectStatus(); x.setId(id); return x; }
    private HealthLevel refHealth(Long id) { HealthLevel x = new HealthLevel(); x.setId(id); return x; }

    @Test
    @DisplayName("create: 合法项目 → 持久化并返回")
    void create_happyPath() {
        Project saved = projectService.create(mkProject("P-2025-100"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCode()).isEqualTo("P-2025-100");
        assertThat(projectRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("create: code 重复 → 抛 BusinessException")
    void create_duplicateCodeThrows() {
        projectService.create(mkProject("P-DUP"));
        Project dup = mkProject("P-DUP");
        assertThatThrownBy(() -> projectService.create(dup))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("P-DUP");
    }

    @Test
    @DisplayName("create: 无效 type id → 抛 BusinessException")
    void create_invalidTypeThrows() {
        Project p = mkProject("P-200");
        p.setType(refType(99999L));
        assertThatThrownBy(() -> projectService.create(p))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("type");
    }

    @Test
    @DisplayName("get: 软删项目 → 抛 404")
    void get_softDeletedReturns404() {
        Project saved = projectService.create(mkProject("P-DEL"));
        projectService.softDelete(saved.getId());
        assertThatThrownBy(() -> projectService.get(saved.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(404);
    }

    @Test
    @DisplayName("update: 局部更新 — 只改 name,其他字段保留")
    void update_partialPreservesUnsetFields() {
        Project saved = projectService.create(mkProject("P-300"));
        Project patch = new Project();
        patch.setName("新名字");
        Project updated = projectService.update(saved.getId(), patch);
        assertThat(updated.getName()).isEqualTo("新名字");
        assertThat(updated.getCode()).isEqualTo("P-300"); // code 未变
        assertThat(updated.getBudgetEstimate()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("softDelete: 删后 list 看不到")
    void softDelete_excludesFromList() {
        Project a = projectService.create(mkProject("P-A"));
        projectService.create(mkProject("P-B"));
        projectService.softDelete(a.getId());
        assertThat(projectService.list()).hasSize(1)
                .extracting(Project::getCode).containsExactly("P-B");
    }
}
