package com.hex.projectgovern.module.project;

import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.module.dict.*;
import com.hex.projectgovern.module.member.ProjectMemberService;
import com.hex.projectgovern.module.project.dto.ProjectCreateRequest;
import com.hex.projectgovern.module.project.dto.ProjectDetailResponse;
import com.hex.projectgovern.module.project.dto.ProjectUpdateRequest;
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
import com.hex.projectgovern.common.testsupport.ContractTestDataInitializer;
import org.springframework.context.annotation.Import;

/**
 * Project DTO 端点契约测试(回归上一轮发现的 4 个问题):
 *  - 用 typeCode/statusCode 字符串,不暴露 id
 *  - 未知 code → 400 业务异常
 *  - 详情 DTO 不再触发 LAZY 反序列化错误
 */
@DataJpaTest
@AutoConfigureTestDatabase
@Import({ProjectService.class, ProjectDtoContractTest.MockConfig.class})
@ActiveProfiles("test")
class ProjectDtoContractTest {

    /** ProjectService 依赖 ProjectMemberService,本测试只验证项目 DTO 契约,提供空 mock */
    @TestConfiguration
    static class MockConfig {
        @Bean
        ProjectMemberService projectMemberService() {
            return org.mockito.Mockito.mock(ProjectMemberService.class);
        }
    }

    @Autowired ProjectService projectService;
    @Autowired ProjectTypeRepository typeRepo;
    @Autowired ProjectStatusRepository statusRepo;
    @Autowired HealthLevelRepository healthRepo;

    private String typeCode = "DELIVERY";
    private String statusCode = "ACTIVE";
    private String healthCode = "GREEN";

    @BeforeEach
    void seedDicts() {
        ProjectType t = new ProjectType();
        t.setCode(typeCode); t.setName("客户交付");
        typeRepo.save(t);

        ProjectStatus s = new ProjectStatus();
        s.setCode(statusCode); s.setName("执行中"); s.setTerminal(false);
        statusRepo.save(s);

        HealthLevel h = new HealthLevel();
        h.setCode(healthCode); h.setName("正常"); h.setColorHex("#67C23A");
        healthRepo.save(h);
    }

    private ProjectCreateRequest mkCreateReq(String code) {
        ProjectCreateRequest r = new ProjectCreateRequest();
        r.setCode(code);
        r.setName("DTO 项目 " + code);
        r.setTypeCode(typeCode);
        r.setStatusCode(statusCode);
        r.setHealthCode(healthCode);
        r.setPlanStartDate(LocalDate.now());
        r.setPlanEndDate(LocalDate.now().plusMonths(3));
        r.setBudgetEstimate(new BigDecimal("100000.00"));
        return r;
    }

    @Test
    @DisplayName("createFromRequest: code 字符串 → 返回 DTO")
    void create_withCodeStrings_succeeds() {
        ProjectDetailResponse d = projectService.createFromRequest(mkCreateReq("P-DTO-001"));
        assertThat(d.id).isNotNull();
        assertThat(d.code).isEqualTo("P-DTO-001");
        assertThat(d.type).isNotNull();
        assertThat(d.type.code).isEqualTo(typeCode);
        assertThat(d.type.name).isEqualTo("客户交付");
        assertThat(d.status.code).isEqualTo(statusCode);
        assertThat(d.health.code).isEqualTo(healthCode);
        assertThat(d.health.colorHex).isEqualTo("#67C23A");
    }

    @Test
    @DisplayName("createFromRequest: 未知 typeCode → 抛异常(防越权传 id)")
    void create_unknownTypeCodeRejected() {
        ProjectCreateRequest r = mkCreateReq("P-BAD-1");
        r.setTypeCode("NONEXISTENT");
        assertThatThrownBy(() -> projectService.createFromRequest(r))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("typeCode");
    }

    @Test
    @DisplayName("createFromRequest: 未知 statusCode → 抛异常")
    void create_unknownStatusCodeRejected() {
        ProjectCreateRequest r = mkCreateReq("P-BAD-2");
        r.setStatusCode("WRONG");
        assertThatThrownBy(() -> projectService.createFromRequest(r))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("statusCode");
    }

    @Test
    @DisplayName("createFromRequest: 重复 code → 抛异常")
    void create_duplicateCodeRejected() {
        projectService.createFromRequest(mkCreateReq("P-DUP"));
        assertThatThrownBy(() -> projectService.createFromRequest(mkCreateReq("P-DUP")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exists");
    }

    @Test
    @DisplayName("getDetail: 返回 DTO 包含字典 code+name(不会 LAZY 报错)")
    void getDetail_returnsDtoWithDictRefs() {
        ProjectDetailResponse created = projectService.createFromRequest(mkCreateReq("P-DET-1"));
        ProjectDetailResponse fetched = projectService.getDetail(created.id);
        assertThat(fetched.code).isEqualTo("P-DET-1");
        assertThat(fetched.type.code).isEqualTo(typeCode);
        assertThat(fetched.status.name).isEqualTo("执行中");
        assertThat(fetched.health.colorHex).isEqualTo("#67C23A");
    }

    @Test
    @DisplayName("getDetail: 不存在 id → 抛 404")
    void getDetail_404OnMissing() {
        assertThatThrownBy(() -> projectService.getDetail(99999L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(404);
    }

    @Test
    @DisplayName("updateFromRequest: healthCode 字符串 → 字典转换")
    void update_healthCodeTranslates() {
        ProjectDetailResponse created = projectService.createFromRequest(mkCreateReq("P-UPD-1"));

        // 加一个 YELLOW 健康度
        HealthLevel y = new HealthLevel();
        y.setCode("YELLOW"); y.setName("关注"); y.setColorHex("#E6A23C");
        healthRepo.save(y);

        ProjectUpdateRequest patch = new ProjectUpdateRequest();
        patch.setHealthCode("YELLOW");
        patch.setName("改名后");
        ProjectDetailResponse updated = projectService.updateFromRequest(created.id, patch);
        assertThat(updated.name).isEqualTo("改名后");
        assertThat(updated.health.code).isEqualTo("YELLOW");
    }

    @Test
    @DisplayName("updateFromRequest: code 字段不在 DTO 中,无法改 code")
    void update_codeNotInDto() {
        ProjectDetailResponse created = projectService.createFromRequest(mkCreateReq("P-IMMUT"));
        ProjectUpdateRequest patch = new ProjectUpdateRequest();
        patch.setName("改个名");
        ProjectDetailResponse updated = projectService.updateFromRequest(created.id, patch);
        assertThat(updated.code).isEqualTo("P-IMMUT");  // code 没变
    }
}
