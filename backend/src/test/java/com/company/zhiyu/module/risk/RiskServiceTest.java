package com.company.zhiyu.module.risk;

import com.company.zhiyu.module.milestone.MilestoneRepository;
import com.company.zhiyu.module.org.UserRepository;
import com.company.zhiyu.module.project.Project;
import com.company.zhiyu.module.project.ProjectRepository;
import com.company.zhiyu.module.risk.dto.RiskRequest;
import com.company.zhiyu.module.wbs.WbsTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * RiskService 单元测试 (P4)。
 * <p>覆盖:
 *  - 风险 CRUD + score/level 自动推导
 *  - code 唯一性 (新建/更新)
 *  - 风险状态变更写 history
 *  - 健康度聚合
 *  - 风险矩阵 (5x5)
 */
@ExtendWith(MockitoExtension.class)
class RiskServiceTest {

    @Mock RiskRepository riskRepository;
    @Mock RiskResponseRepository responseRepository;
    @Mock RiskHistoryRepository historyRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock WbsTaskRepository wbsTaskRepository;
    @Mock MilestoneRepository milestoneRepository;

    RiskService riskService;

    @BeforeEach
    void init() {
        riskService = new RiskService(riskRepository, responseRepository, historyRepository,
                projectRepository, userRepository, wbsTaskRepository, milestoneRepository);
    }

    private Project stubProject() {
        Project p = new Project();
        p.setId(100L);
        p.setCode("P-TEST");
        p.setName("测试项目");
        return p;
    }

    @Test
    @DisplayName("save 新建: 自动算 score/level, 写 CREATED history")
    void save_create_computesScoreAndAppendsHistory() {
        RiskRequest req = new RiskRequest(
                null, 100L, "R-001", "技术风险", "描述",
                "TECHNICAL", 4, 5,
                "OPEN", 7L, "提前评估", null, "MITIGATE",
                null, null, null, null);
        when(projectRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(stubProject()));
        when(riskRepository.countByProjectIdAndCodeAndDeletedFalse(100L, "R-001"))
                .thenReturn(0L);
        when(riskRepository.save(any(Risk.class)))
                .thenAnswer(inv -> {
                    Risk r = inv.getArgument(0);
                    r.setId(1L);
                    return r;
                });

        var resp = riskService.save(req, 99L);

        assertThat(resp.code()).isEqualTo("R-001");
        assertThat(resp.probability()).isEqualTo(4);
        assertThat(resp.impact()).isEqualTo(5);
        assertThat(resp.score()).isEqualTo(20);
        assertThat(resp.level()).isEqualTo("CRITICAL");
        // 写了一条 CREATED history
        verify(historyRepository).save(any(RiskHistory.class));
    }

    @Test
    @DisplayName("save 更新: 改 status/score/level 自动写 STATUS_CHANGED / SCORE_CHANGED history")
    void save_update_writesChangeHistories() {
        Risk existing = new Risk();
        existing.setId(1L);
        existing.setProjectId(100L);
        existing.setCode("R-001");
        existing.setTitle("旧标题");
        existing.setCategory("TECHNICAL");
        existing.setProbability(2);
        existing.setImpact(2);
        existing.setScore(4);
        existing.setLevel("LOW");
        existing.setStatus("OPEN");
        existing.setOwnerUserId(7L);

        RiskRequest req = new RiskRequest(
                1L, 100L, "R-001", "新标题", "新描述",
                "TECHNICAL", 5, 5,  // prob 2→5, impact 2→5
                "MITIGATING", 8L, "新措施", null, "AVOID",
                null, null, null, null);
        when(projectRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(stubProject()));
        when(riskRepository.findActiveById(1L)).thenReturn(Optional.of(existing));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = riskService.save(req, 99L);

        assertThat(resp.score()).isEqualTo(25);
        assertThat(resp.level()).isEqualTo("CRITICAL");
        assertThat(resp.status()).isEqualTo("MITIGATING");
        assertThat(resp.ownerUserId()).isEqualTo(8L);
        // 至少 3 条 history: STATUS_CHANGED + SCORE_CHANGED + LEVEL_CHANGED + OWNER_CHANGED
        verify(historyRepository, atLeast(4)).save(any(RiskHistory.class));
    }

    @Test
    @DisplayName("save 新建: code 重复 → 抛 BusinessException")
    void save_duplicateCode_throws() {
        RiskRequest req = new RiskRequest(
                null, 100L, "R-001", "x", null,
                "TECHNICAL", 3, 3, "OPEN", null, null, null, null,
                null, null, null, null);
        when(projectRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(stubProject()));
        when(riskRepository.countByProjectIdAndCodeAndDeletedFalse(100L, "R-001"))
                .thenReturn(1L);

        assertThatThrownBy(() -> riskService.save(req, null))
                .hasMessageContaining("风险编号已存在");
    }

    @Test
    @DisplayName("healthSummary: 返回 KPI 6 个值 + byCategory / byLevel")
    void healthSummary_returnsAggregates() {
        when(projectRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(stubProject()));
        // mock PG 形态: 顶层 Object[] 直接 = [v0..v5]
        when(riskRepository.aggregateHealth(100L))
                .thenReturn(new Object[]{ 5L, 3L, 1L, 1L, 0L, 20L });
        Risk r1 = mkRisk(1L, 100L, "R-001", "TECHNICAL", 4, 5, "CRITICAL", "OPEN");
        Risk r2 = mkRisk(2L, 100L, "R-002", "SCHEDULE",   3, 3, "HIGH",     "MITIGATING");
        when(riskRepository.findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(100L))
                .thenReturn(List.of(r1, r2));

        // byCategory / byLevel 需要从 all 里 group, 但 all 实际只有 2 个, so
        // byCategory 应该恰好有 TECHNICAL + SCHEDULE 各 1
        var s = riskService.healthSummary(100L);

        assertThat(s.totalCount()).isEqualTo(5L);
        assertThat(s.activeCount()).isEqualTo(3L);
        assertThat(s.criticalActive()).isEqualTo(1L);
        assertThat(s.highActive()).isEqualTo(1L);
        assertThat(s.occurredCount()).isEqualTo(0L);
        assertThat(s.maxActiveScore()).isEqualTo(20);
        assertThat(s.byCategory()).containsKeys("TECHNICAL", "SCHEDULE");
    }

    @Test
    @DisplayName("matrix: 5x5 网格, 总 cell=25, count 累加正确")
    void matrix_returns5x5Grid() {
        when(projectRepository.findByIdAndDeletedFalse(100L))
                .thenReturn(Optional.of(stubProject()));
        Risk r = mkRisk(1L, 100L, "R-001", "TECHNICAL", 5, 5, "CRITICAL", "OPEN");
        when(riskRepository.findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(100L))
                .thenReturn(List.of(r));

        var m = riskService.matrix(100L);

        assertThat(m.cells()).hasSize(25);
        // (5,5) 应该 count=1
        var cell55 = m.get(5, 5);
        assertThat(cell55.count()).isEqualTo(1);
        assertThat(cell55.risks()).hasSize(1);
        // (1,1) 应该 count=0
        assertThat(m.get(1, 1).count()).isEqualTo(0);
    }

    @Test
    @DisplayName("softDelete: 写 STATUS_CHANGED history + deleted=true")
    void softDelete_writesHistory() {
        Risk r = new Risk();
        r.setId(1L);
        r.setProjectId(100L);
        r.setCode("R-001");
        r.setDeleted(false);
        when(riskRepository.findActiveById(1L)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        riskService.softDelete(1L, 99L);

        assertThat(r.isDeleted()).isTrue();
        verify(historyRepository).save(any(RiskHistory.class));
    }

    private static Risk mkRisk(Long id, Long projectId, String code, String category,
                                int p, int i, String level, String status) {
        Risk r = new Risk();
        r.setId(id);
        r.setProjectId(projectId);
        r.setCode(code);
        r.setTitle("t");
        r.setCategory(category);
        r.setProbability(p);
        r.setImpact(i);
        r.setScore(p * i);
        r.setLevel(level);
        r.setStatus(status);
        return r;
    }
}
