package com.hex.projectgovern.module.healthadvisor;

import com.hex.projectgovern.module.dict.HealthLevel;
import com.hex.projectgovern.module.dict.HealthLevelRepository;
import com.hex.projectgovern.module.dict.MilestoneStatus;
import com.hex.projectgovern.module.dict.MilestoneStatusRepository;
import com.hex.projectgovern.module.dict.ProjectStatus;
import com.hex.projectgovern.module.dict.ProjectStatusRepository;
import com.hex.projectgovern.module.dict.ProjectType;
import com.hex.projectgovern.module.dict.ProjectTypeRepository;
import com.hex.projectgovern.module.milestone.Milestone;
import com.hex.projectgovern.module.milestone.MilestonePhase;
import com.hex.projectgovern.module.milestone.MilestonePhaseRepository;
import com.hex.projectgovern.module.milestone.MilestoneRepository;
import com.hex.projectgovern.module.project.Project;
import com.hex.projectgovern.module.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HealthAdvisor(纯函数) + HealthAdvisorService 跑批 测试。
 *
 * 关键边界:
 *  - 终态项目(CLOSED/REJECTED/DRAFT/PENDING)→ suggestedCode=null
 *  - 进度正常且不超期 → GREEN
 *  - 轻微超期(1-29 天)或轻微落后(>=80% 期望值) → YELLOW
 *  - 严重超期(>=30 天)或严重落后(<50% 期望值) → RED
 *  - 跑批 apply=true 时,会写回 project.health
 *  - apply=true 但建议与当前一致时,不写回
 */
@DataJpaTest
@AutoConfigureTestDatabase
@Import(HealthAdvisorService.class)
@ActiveProfiles("test")
class HealthAdvisorServiceTest {

    @Autowired HealthAdvisorService advisorService;
    @Autowired ProjectRepository projectRepository;
    @Autowired MilestoneRepository milestoneRepository;
    @Autowired HealthLevelRepository healthRepo;
    @Autowired ProjectStatusRepository statusRepo;
    @Autowired ProjectTypeRepository typeRepo;
    @Autowired MilestoneStatusRepository milestoneStatusRepo;
    @Autowired MilestonePhaseRepository milestonePhaseRepo;

    private ProjectStatus active, closed;
    private HealthLevel green, yellow, red;
    private ProjectType delivery;
    private MilestoneStatus msPending, msCompleted, msDelayed;
    private final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @BeforeEach
    void seedDicts() {
        // 清干净,避免跨测试污染
        milestoneRepository.deleteAll();
        projectRepository.deleteAll();

        delivery = new ProjectType();
        delivery.setCode("DELIVERY"); delivery.setName("客户交付");
        typeRepo.save(delivery);

        active = new ProjectStatus();
        active.setCode("ACTIVE"); active.setName("执行中"); active.setTerminal(false);
        statusRepo.save(active);

        closed = new ProjectStatus();
        closed.setCode("CLOSED"); closed.setName("已结项"); closed.setTerminal(true);
        statusRepo.save(closed);

        green = new HealthLevel();
        green.setCode("GREEN"); green.setName("正常"); green.setColorHex("#67C23A");
        healthRepo.save(green);

        yellow = new HealthLevel();
        yellow.setCode("YELLOW"); yellow.setName("关注"); yellow.setColorHex("#E6A23C");
        healthRepo.save(yellow);

        red = new HealthLevel();
        red.setCode("RED"); red.setName("严重"); red.setColorHex("#F56C6C");
        healthRepo.save(red);

        msPending = new MilestoneStatus();
        msPending.setCode("PENDING"); msPending.setName("未开始"); msPending.setTerminal(false);
        milestoneStatusRepo.save(msPending);

        msCompleted = new MilestoneStatus();
        msCompleted.setCode("COMPLETED"); msCompleted.setName("已完成"); msCompleted.setTerminal(true);
        milestoneStatusRepo.save(msCompleted);

        msDelayed = new MilestoneStatus();
        msDelayed.setCode("DELAYED"); msDelayed.setName("已延期"); msDelayed.setTerminal(false);
        milestoneStatusRepo.save(msDelayed);
    }

    private Project mkProject(String code, ProjectStatus status,
                              LocalDate start, LocalDate end) {
        Project p = new Project();
        p.setCode(code); p.setName("P " + code);
        p.setType(delivery); p.setStatus(status);
        p.setPlanStartDate(start); p.setPlanEndDate(end);
        return p;
    }

    private Milestone mkMilestone(Project p, String name, int seq, int weight, MilestoneStatus st) {
        Milestone m = new Milestone();
        m.setProjectId(p.getId());
        m.setName(name); m.setSequence(seq);
        m.setPlanDate(p.getPlanEndDate() != null ? p.getPlanEndDate().minusDays(7) : LocalDate.now().plusDays(7));
        m.setWeight(weight);
        m.setStatus(st);
        m.setPhaseId(phaseId());  // 修复 V3.1 phase_id NOT NULL
        return milestoneRepository.save(m);
    }

    /** V3.1 milestone.phase_id NOT NULL — 测试 seed 一个开发阶段 (id=4) */
    private long phaseId() {
        if (milestonePhaseRepo.count() == 0) {
            MilestonePhase dev = new MilestonePhase();
            dev.setId(4L); dev.setCode("DEV"); dev.setName("开发"); dev.setSortOrder(4);
            milestonePhaseRepo.save(dev);
        }
        return 4L;
    }

    @Test
    @DisplayName("GREEN: 不超期 + 进度按计划 → 建议 GREEN")
    void caseGreen() {
        LocalDate today = LocalDate.now();
        Project p = projectRepository.save(mkProject("G1", active, today.minusDays(50), today.plusDays(50)));
        mkMilestone(p, "M1", 1, 1, msCompleted);
        mkMilestone(p, "M2", 2, 1, msPending);

        HealthSuggestion s = advisorService.suggestForProject(p.getId());
        assertThat(s.getSuggestedCode()).isEqualTo("GREEN");
        assertThat(s.getOverdueDays()).isEqualTo(0);
        assertThat(s.getMilestoneCompletionPct()).isEqualTo(50);
    }

    @Test
    @DisplayName("YELLOW: 超期 5 天(<30 阈值) → YELLOW")
    void caseYellow_overdue() {
        LocalDate today = LocalDate.now();
        Project p = projectRepository.save(mkProject("Y1", active, today.minusDays(100), today.minusDays(5)));
        mkMilestone(p, "M1", 1, 1, msCompleted);

        HealthSuggestion s = advisorService.suggestForProject(p.getId());
        assertThat(s.getSuggestedCode()).isEqualTo("YELLOW");
        assertThat(s.getOverdueDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("RED: 超期 40 天(>=30 阈值) → RED")
    void caseRed_overdue() {
        LocalDate today = LocalDate.now();
        Project p = projectRepository.save(mkProject("R1", active, today.minusDays(140), today.minusDays(40)));
        mkMilestone(p, "M1", 1, 1, msCompleted);

        HealthSuggestion s = advisorService.suggestForProject(p.getId());
        assertThat(s.getSuggestedCode()).isEqualTo("RED");
        assertThat(s.getOverdueDays()).isEqualTo(40);
    }

    @Test
    @DisplayName("RED: 进度严重落后(已过 80%,只完成 1/4) → RED")
    void caseRed_lagging() {
        LocalDate today = LocalDate.now();
        Project p = projectRepository.save(mkProject("R2", active, today.minusDays(80), today.plusDays(20)));
        // 4 个里程碑,只完成 1 个
        mkMilestone(p, "M1", 1, 1, msCompleted);
        mkMilestone(p, "M2", 2, 1, msPending);
        mkMilestone(p, "M3", 3, 1, msPending);
        mkMilestone(p, "M4", 4, 1, msPending);

        HealthSuggestion s = advisorService.suggestForProject(p.getId());
        assertThat(s.getSuggestedCode()).isEqualTo("RED");
        assertThat(s.getMilestoneCompletionPct()).isEqualTo(25);
    }

    @Test
    @DisplayName("CLOSED 项目: suggestedCode=null,跳过")
    void caseClosed_skipped() {
        LocalDate today = LocalDate.now();
        Project p = projectRepository.save(mkProject("C1", closed, today.minusDays(200), today.minusDays(50)));

        HealthSuggestion s = advisorService.suggestForProject(p.getId());
        assertThat(s.getSuggestedCode()).isNull();
        assertThat(s.getSuggestedName()).isEqualTo("(跳过)");
    }

    @Test
    @DisplayName("PENDING 项目: suggestedCode=null,跳过")
    void casePending_skipped() {
        LocalDate today = LocalDate.now();
        // 临时造一个 PENDING 状态
        ProjectStatus pending = new ProjectStatus();
        pending.setCode("PENDING"); pending.setName("待立项"); pending.setTerminal(false);
        statusRepo.save(pending);

        Project p = projectRepository.save(mkProject("P1", pending, today, today.plusDays(30)));
        HealthSuggestion s = advisorService.suggestForProject(p.getId());
        assertThat(s.getSuggestedCode()).isNull();
    }

    @Test
    @DisplayName("apply=true 跑批: 健康度被写回 project.health")
    void runForAll_applyWrites() {
        LocalDate today = LocalDate.now();
        // A1: 进度 100% 但超期 5 天(无里程碑拖累)→ YELLOW
        Project p1 = projectRepository.save(mkProject("A1", active, today.minusDays(100), today.minusDays(5)));
        mkMilestone(p1, "M1", 1, 1, msCompleted);
        // A2: 进度按计划,不超期 → GREEN
        Project p2 = projectRepository.save(mkProject("A2", active, today.minusDays(50), today.plusDays(50)));
        mkMilestone(p2, "M1", 1, 1, msCompleted);
        mkMilestone(p2, "M2", 2, 1, msPending);

        var results = advisorService.runForAll(true);
        // 至少评估了 2 个
        assertThat(results.size()).isGreaterThanOrEqualTo(2);

        // 写回后 DB 读出来
        Project p1Reloaded = projectRepository.findByIdAndDeletedFalse(p1.getId()).orElseThrow();
        Project p2Reloaded = projectRepository.findByIdAndDeletedFalse(p2.getId()).orElseThrow();
        assertThat(p1Reloaded.getHealth().getCode()).isEqualTo("YELLOW");
        assertThat(p2Reloaded.getHealth().getCode()).isEqualTo("GREEN");
    }
}
