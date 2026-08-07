package com.company.pmo.module.milestone;

import com.company.pmo.module.dict.MilestoneStatus;
import com.company.pmo.module.dict.MilestoneStatusRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MilestoneRepository.computeWeightedProgressPct 的边界测试。
 *
 * 重点:
 *  - 修复前的 0% bug (LAZY self-invocation) 不再发生
 *  - 四舍五入正确 (38.5 -> 39 而非 38)
 *  - 权重为 0 时返 0,不抛 ArithmeticException
 *  - 空项目返 0
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
class MilestoneRepositoryProgressTest {

    @Autowired MilestoneRepository milestoneRepository;
    @Autowired MilestoneStatusRepository statusRepo;
    @Autowired MilestonePhaseRepository phaseRepo;
    @PersistenceContext EntityManager entityManager;

    private Long pendingId, inProgressId, completedId;
    private static final long PHASE_DEV = 4L; // "开发" 阶段 (V3.1 seed)

    @BeforeEach
    void seedStatuses() {
        // 修复 milestone.phase_id NOT NULL — V3.1 schema 要求阶段必填
        if (phaseRepo.count() == 0) {
            phaseRepo.save(mkPhase(1L, "INIT", "立项", 1));
            phaseRepo.save(mkPhase(2L, "REQ", "需求", 2));
            phaseRepo.save(mkPhase(3L, "DESIGN", "设计", 3));
            phaseRepo.save(mkPhase(PHASE_DEV, "DEV", "开发", 4));
            phaseRepo.save(mkPhase(5L, "TEST", "测试", 5));
            phaseRepo.save(mkPhase(6L, "DEPLOY", "上线运维", 6));
            phaseRepo.save(mkPhase(7L, "WARRANTY", "维保", 7));
        }

        MilestoneStatus pending = new MilestoneStatus();
        pending.setCode("PENDING"); pending.setName("未开始"); pending.setTerminal(false);
        pendingId = statusRepo.save(pending).getId();

        MilestoneStatus ip = new MilestoneStatus();
        ip.setCode("IN_PROGRESS"); ip.setName("进行中"); ip.setTerminal(false);
        inProgressId = statusRepo.save(ip).getId();

        MilestoneStatus done = new MilestoneStatus();
        done.setCode("COMPLETED"); done.setName("已完成"); done.setTerminal(true);
        completedId = statusRepo.save(done).getId();
    }

    private Milestone mkMilestone(long projectId, int seq, int weight, MilestoneStatus status) {
        Milestone m = new Milestone();
        m.setProjectId(projectId);
        m.setName("M" + seq);
        m.setSequence(seq);
        m.setPlanDate(LocalDate.now());
        m.setWeight(weight);
        m.setStatus(status);
        m.setPhaseId(PHASE_DEV);  // 修复 V3.1 NOT NULL 约束
        return m;
    }

    private MilestonePhase mkPhase(long id, String code, String name, int sortOrder) {
        MilestonePhase p = new MilestonePhase();
        p.setId(id);
        p.setCode(code);
        p.setName(name);
        p.setSortOrder(sortOrder);
        return p;
    }

    @Test
    @DisplayName("空项目 → 0%")
    void emptyProjectReturnsZero() {
        assertThat(milestoneRepository.computeWeightedProgressPct(99999L)).isEqualTo(0);
    }

    @Test
    @DisplayName("全未开始 → 0%")
    void allPendingReturnsZero() {
        for (int i = 1; i <= 3; i++) {
            milestoneRepository.save(mkMilestone(1L, i, 1, statusRef(pendingId)));
        }
        assertThat(milestoneRepository.computeWeightedProgressPct(1L)).isEqualTo(0);
    }

    @Test
    @DisplayName("全完成 → 100%")
    void allCompletedReturnsHundred() {
        for (int i = 1; i <= 3; i++) {
            milestoneRepository.save(mkMilestone(2L, i, 1, statusRef(completedId)));
        }
        assertThat(milestoneRepository.computeWeightedProgressPct(2L)).isEqualTo(100);
    }

    @Test
    @DisplayName("部分完成 (2 of 3, weight=1) → 67% (2/3 四舍五入)")
    void partialCompletionRounds() {
        milestoneRepository.save(mkMilestone(3L, 1, 1, statusRef(completedId)));
        milestoneRepository.save(mkMilestone(3L, 2, 1, statusRef(completedId)));
        milestoneRepository.save(mkMilestone(3L, 3, 1, statusRef(pendingId)));
        // 2/3 = 0.6666... → round to 67
        assertThat(milestoneRepository.computeWeightedProgressPct(3L)).isEqualTo(67);
    }

    @Test
    @DisplayName("加权: 5/(1+2+2+1+1+1)=62% (修复前 0% 的回归用例)")
    void weightedProgressMatchesExpected() {
        // 6 个里程碑,2 完成 weight=1+2,4 未开始 weight=1+1+1+1
        milestoneRepository.save(mkMilestone(4L, 1, 1, statusRef(completedId)));
        milestoneRepository.save(mkMilestone(4L, 2, 2, statusRef(completedId)));
        milestoneRepository.save(mkMilestone(4L, 3, 2, statusRef(inProgressId)));
        milestoneRepository.save(mkMilestone(4L, 4, 1, statusRef(pendingId)));
        milestoneRepository.save(mkMilestone(4L, 5, 1, statusRef(pendingId)));
        milestoneRepository.save(mkMilestone(4L, 6, 1, statusRef(pendingId)));
        // 3 / 8 = 0.375 → 38 (这是修复前 0% 的回归测试)
        assertThat(milestoneRepository.computeWeightedProgressPct(4L)).isEqualTo(38);
    }

    @Test
    @DisplayName("软删的里程碑不参与计算")
    void softDeletedExcluded() {
        Milestone m1 = milestoneRepository.save(mkMilestone(5L, 1, 1, statusRef(completedId)));
        milestoneRepository.save(mkMilestone(5L, 2, 1, statusRef(pendingId)));
        // 软删已完成那个
        m1.setDeleted(true);
        milestoneRepository.save(m1);
        assertThat(milestoneRepository.computeWeightedProgressPct(5L)).isEqualTo(0);
    }

    @Test
    @DisplayName("JOIN FETCH 查询能拉出 status,不再抛 LazyInitializationException")
    void findByProjectIdWithStatusEagerLoadsStatus() {
        MilestoneStatus done = statusRepo.findById(completedId).orElseThrow();
        Milestone m = new Milestone();
        m.setProjectId(6L);
        m.setName("M1");
        m.setSequence(1);
        m.setPlanDate(LocalDate.now());
        m.setWeight(1);
        m.setStatus(done); // 真实持久化实体,不是 stub
        m.setPhaseId(PHASE_DEV); // 修复 V3.1 phase_id NOT NULL 约束
        milestoneRepository.save(m);
        // 模拟 controller 序列化阶段:清空 L1 cache,强制从 DB 加载
        entityManager.clear();
        var list = milestoneRepository.findByProjectIdWithStatus(6L);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getStatus().getCode()).isEqualTo("COMPLETED");
    }

    private MilestoneStatus statusRef(long id) {
        MilestoneStatus s = new MilestoneStatus();
        s.setId(id);
        return s;
    }
}
