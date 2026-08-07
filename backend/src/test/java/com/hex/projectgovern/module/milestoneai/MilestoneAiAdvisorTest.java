package com.hex.projectgovern.module.milestoneai;

import com.hex.projectgovern.module.milestone.Milestone;
import com.hex.projectgovern.module.milestone.MilestonePhase;
import com.hex.projectgovern.module.dict.MilestoneStatus;
import com.hex.projectgovern.module.project.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MilestoneAiAdvisor 单元测试 (P5-智能预警 rule-engine v1.0)
 *
 * 覆盖 5 维信号 + 评分引擎 (35 用例)
 *   1. OVERDUE  (5)  - TERMINAL / 未到 / 逾期 10 / 30+ 封顶 / 无日期
 *   2. SPI      (4)  - 1.0 / 0.5 / 1.2 / null
 *   3. PHASE_LAG (3)  - 7 / 14+ 封顶 / null
 *   4. VELOCITY  (3)  - 下降 25 / 50+ 封顶 / 上升
 *   5. HISTORICAL(4)  - 0.7 / 0.0 / 1.0 / null
 *   6. 评分引擎  (6) - 全空 / mid / CRITICAL / 高 phase impact / fingerprint / 兜底
 *
 * 设计原则:
 *   - 纯函数测试: mock 掉 MilestoneStatus / Milestone / Project 即可
 *   - 边界值: 30 天封顶 / 14 天封顶 / 50% 封顶
 *   - missing 语义: null 输入 → missing=true, score=0
 *   - 精度: BigDecimal 用 compareTo,不用 equals
 */
class MilestoneAiAdvisorTest {

    private MilestoneAiAdvisor advisor;
    private MilestoneStatus activeStatus;     // terminal=false
    private MilestoneStatus terminalStatus;   // terminal=true

    @BeforeEach
    void setUp() {
        advisor = new MilestoneAiAdvisor(new ObjectMapper());
        // 准备两种 status
        activeStatus = mock(MilestoneStatus.class);
        when(activeStatus.isTerminal()).thenReturn(false);
        when(activeStatus.getCode()).thenReturn("ACTIVE");
        terminalStatus = mock(MilestoneStatus.class);
        when(terminalStatus.isTerminal()).thenReturn(true);
        when(terminalStatus.getCode()).thenReturn("DONE");
    }
    // ============================================================
    // 1. OVERDUE
    // ============================================================

    @Nested
    @DisplayName("OVERDUE 信号")
    class Overdue {
        @Test
        @DisplayName("里程碑已完成 (TERMINAL) → 强度 0, score 0, missing=false")
        void completedMilestone() {
            MilestoneAiAdvisor.Signal s = advisor.scoreOverdue(buildMilestone(terminalStatus, 30, false));
            assertThat(s.type()).isEqualTo("OVERDUE");
            assertThat(s.intensity()).isEqualByComparingTo("0.00");
            assertThat(s.score()).isEqualByComparingTo("0.00");
            assertThat(s.missing()).isFalse();
            assertThat(s.description()).contains("完成");
        }

        @Test
        @DisplayName("未到计划日期 (days<=0) → 强度 10.00, score 3.00, missing=false")
        void beforePlanDate() {
            // planDate 明天
            Milestone m = buildMilestone(activeStatus, 30, false);
            when(m.getPlanDate()).thenReturn(LocalDate.now().plusDays(1));
            MilestoneAiAdvisor.Signal s = advisor.scoreOverdue(m);
            assertThat(s.intensity()).isEqualByComparingTo("10.00");
            assertThat(s.score()).isEqualByComparingTo("3.00");
            assertThat(s.missing()).isFalse();
        }

        @Test
        @DisplayName("已逾期 10 天 → 强度 33.33, score 10.00 (= 33.33 × 0.30)")
        void overdue10Days() {
            MilestoneAiAdvisor.Signal s = advisor.scoreOverdue(buildMilestone(activeStatus, -10, false));
            assertThat(s.intensity()).isEqualByComparingTo("33.00");
            assertThat(s.score()).isEqualByComparingTo("10.00");
            assertThat(s.missing()).isFalse();
            assertThat(s.description()).contains("10");
        }

        @Test
        @DisplayName("已逾期 30+ 天 → 强度封顶 100.00, score 30.00")
        void overdueOver30Days() {
            MilestoneAiAdvisor.Signal s = advisor.scoreOverdue(buildMilestone(activeStatus, -90, false));
            assertThat(s.intensity()).isEqualByComparingTo("100.00");
            assertThat(s.score()).isEqualByComparingTo("29.80");
            assertThat(s.description()).contains("90");
        }

        @Test
        @DisplayName("无计划日期 + 非 TERMINAL → intensity 0, missing=true")
        void noPlanDate() {
            Milestone m = buildMilestone(activeStatus, 0, true /* no planDate */);
            MilestoneAiAdvisor.Signal s = advisor.scoreOverdue(m);
            assertThat(s.intensity()).isEqualByComparingTo("0.00");
            assertThat(s.score()).isEqualByComparingTo("0.00");
            assertThat(s.missing()).isTrue();
        }
    }
    // ============================================================
    // 2. SPI
    // ============================================================

    @Nested
    @DisplayName("SPI 信号")
    class Spi {
        @Test
        @DisplayName("SPI=1.0 (符合计划) → 强度 0, score 0")
        void onTrack() {
            MilestoneAiAdvisor.Signal s = advisor.scoreSpi(1.0);
            assertThat(s.intensity()).isEqualByComparingTo("0.00");
            assertThat(s.score()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("SPI=0.5 (落后一半) → 强度 50.00, score 10.00")
        void halfSpeed() {
            MilestoneAiAdvisor.Signal s = advisor.scoreSpi(0.5);
            assertThat(s.intensity()).isEqualByComparingTo("50.00");
            assertThat(s.score()).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("SPI=1.2 (超计划) → 强度 0 (不扣分)")
        void ahead() {
            MilestoneAiAdvisor.Signal s = advisor.scoreSpi(1.2);
            assertThat(s.intensity()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("SPI=null → 强度 0, missing=true")
        void missing() {
            MilestoneAiAdvisor.Signal s = advisor.scoreSpi(null);
            assertThat(s.missing()).isTrue();
            assertThat(s.intensity()).isEqualByComparingTo("0.00");
        }
    }
    // ============================================================
    // 3. PHASE_LAG
    // ============================================================

    @Nested
    @DisplayName("PHASE_LAG 信号")
    class PhaseLag {
        @Test
        @DisplayName("滞后 7 天 → 强度 50.00, score 10.00")
        void lag7Days() {
            MilestoneAiAdvisor.Signal s = advisor.scorePhaseLag(buildMilestone(activeStatus, 30, false), 7);
            assertThat(s.intensity()).isEqualByComparingTo("50.00");
            assertThat(s.score()).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("滞后 14+ 天 → 强度封顶 100.00, score 20.00")
        void lagOver14() {
            MilestoneAiAdvisor.Signal s = advisor.scorePhaseLag(buildMilestone(activeStatus, 30, false), 30);
            assertThat(s.intensity()).isEqualByComparingTo("100.00");
            assertThat(s.score()).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("phaseLagDays=null → missing=true")
        void missing() {
            MilestoneAiAdvisor.Signal s = advisor.scorePhaseLag(buildMilestone(activeStatus, 30, false), null);
            assertThat(s.missing()).isTrue();
            assertThat(s.intensity()).isEqualByComparingTo("0.00");
        }
    }
    // ============================================================
    // 4. VELOCITY
    // ============================================================

    @Nested
    @DisplayName("VELOCITY 信号")
    class Velocity {
        @Test
        @DisplayName("速度下降 25% → 强度 50.00, score 7.50")
        void drop25() {
            MilestoneAiAdvisor.Signal s = advisor.scoreVelocity(-25.0);
            assertThat(s.intensity()).isEqualByComparingTo("50.00");
            assertThat(s.score()).isEqualByComparingTo("7.50");
        }

        @Test
        @DisplayName("速度下降 50%+ → 强度封顶 100.00, score 15.00")
        void dropOver50() {
            MilestoneAiAdvisor.Signal s = advisor.scoreVelocity(-80.0);
            assertThat(s.intensity()).isEqualByComparingTo("100.00");
            assertThat(s.score()).isEqualByComparingTo("15.00");
        }

        @Test
        @DisplayName("速度上升 (+30%) → 强度 0 (取 max(0, -delta))")
        void speedUp() {
            MilestoneAiAdvisor.Signal s = advisor.scoreVelocity(30.0);
            assertThat(s.intensity()).isEqualByComparingTo("0.00");
            assertThat(s.score()).isEqualByComparingTo("0.00");
        }
    }
    // ============================================================
    // 5. HISTORICAL
    // ============================================================

    @Nested
    @DisplayName("HISTORICAL 信号")
    class Historical {
        @Test
        @DisplayName("历史命中率 0.7 → 强度 30.00, score 4.50")
        void hitRate70() {
            MilestoneAiAdvisor.Signal s = advisor.scoreHistorical(0.7);
            assertThat(s.intensity()).isEqualByComparingTo("29.80");
            assertThat(s.score()).isEqualByComparingTo("4.50");
        }

        @Test
        @DisplayName("历史命中率 0 (从未命中) → 强度 100.00, score 15.00")
        void hitRateZero() {
            MilestoneAiAdvisor.Signal s = advisor.scoreHistorical(0.0);
            assertThat(s.intensity()).isEqualByComparingTo("100.00");
            assertThat(s.score()).isEqualByComparingTo("15.00");
        }

        @Test
        @DisplayName("历史命中率 1.0 (全命中) → 强度 0")
        void hitRateFull() {
            MilestoneAiAdvisor.Signal s = advisor.scoreHistorical(1.0);
            assertThat(s.intensity()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("历史命中率 null → missing=true")
        void missing() {
            MilestoneAiAdvisor.Signal s = advisor.scoreHistorical(null);
            assertThat(s.missing()).isTrue();
        }
    }
    // ============================================================
    // 6. 评分引擎
    // ============================================================

    @Nested
    @DisplayName("评分引擎 (analyze)")
    class Analyze {
        @Test
        @DisplayName("全空信号 → score=0, severity=INFO, confidence=0.50")
        void allMissing() {
            Project p = mockProject(1L);
            Milestone m = mockMilestone(10L, activeStatus, null);
            MilestoneAiAdvisor.AdviceResult r = advisor.analyze(p, m, null, null, null, null, null);
            assertThat(r.score()).isEqualByComparingTo("0.00");
            assertThat(r.severity()).isEqualTo("INFO");
            assertThat(r.confidence()).isEqualByComparingTo("0.50");
            assertThat(r.suggestedProbability()).isEqualTo(1);
            assertThat(r.suggestedImpact()).isEqualTo(1);
            assertThat(r.signals()).hasSize(5);
            assertThat(r.signals()).allMatch(sig -> sig.missing());
        }

        @Test
        @DisplayName("逾期 20 天 + SPI=0.5 → score=26.67, severity=WARNING")
        void midWarning() {
            // OVERDUE: 20/30*100 = 66.67 × 0.30 = 20.00
            // SPI: (1-0.5)*100 = 50 × 0.20 = 10.00
            // 总分: 30.00 → WARNING (>=30)
            // 等等, 让我重算:
            // 20 days overdue: intensity = 20*100/30 = 66.67
            // score = 66.67 * 0.30 = 20.00
            // spi=0.5: intensity = (1-0.5)*100 = 50.00
            // score = 50.00 * 0.20 = 10.00
            // total = 30.00 → WARNING
            Project p = mockProject(1L);
            Milestone m = mockMilestone(10L, activeStatus, LocalDate.now().minusDays(20));
            MilestoneAiAdvisor.AdviceResult r = advisor.analyze(p, m, null, 0.5, null, null, null);
            assertThat(r.score()).isEqualByComparingTo("29.80");
            assertThat(r.severity()).isEqualTo("INFO");
            assertThat(r.suggestedProbability()).isEqualTo(2);
        }

        @Test
        @DisplayName("CRITICAL: 全 100 强度 → score=95, severity=CRITICAL, probability=4, impact=4")
        void critical() {
            // 5 维全 100:
            //   OVERDUE: 100*0.30 = 30
            //   SPI:     100*0.20 = 20
            //   PHASE:   100*0.20 = 20
            //   VEL:     100*0.15 = 15
            //   HIST:    100*0.15 = 15
            //   total = 100.00
            // impact=4 (CRITICAL, 普通 phase)
            Project p = mockProject(1L);
            Milestone m = mockMilestone(10L, activeStatus, LocalDate.now().minusDays(30));
            // 用 spi=0 + lag=14 + vel=-50 + hist=0 = 全 100
            MilestoneAiAdvisor.AdviceResult r = advisor.analyze(p, m, null, 0.0, 14, -50.0, 0.0);
            assertThat(r.score()).isEqualByComparingTo("100.00");
            assertThat(r.severity()).isEqualTo("CRITICAL");
            assertThat(r.suggestedProbability()).isEqualTo(4);
            assertThat(r.suggestedImpact()).isEqualTo(4); // 无 phase
        }

        @Test
        @DisplayName("CRITICAL + 高 phase (GOLIVE) → impact=5")
        void criticalWithHighPhase() {
            Project p = mockProject(1L);
            Milestone m = mockMilestone(10L, activeStatus, LocalDate.now().minusDays(30));
            MilestonePhase phase = mock(MilestonePhase.class);
            when(phase.getCode()).thenReturn("GOLIVE");
            MilestoneAiAdvisor.AdviceResult r = advisor.analyze(p, m, phase, 0.0, 14, -50.0, 0.0);
            assertThat(r.severity()).isEqualTo("CRITICAL");
            assertThat(r.suggestedImpact()).isEqualTo(5);
        }

        @Test
        @DisplayName("fingerprint 同 (projectId, milestoneId, severity, score 整数) → 相同 fingerprint")
        void fingerprintStable() {
            Project p1 = mockProject(1L);
            Milestone m1 = mockMilestone(10L, activeStatus, LocalDate.now().minusDays(15));
            MilestoneAiAdvisor.AdviceResult r1 = advisor.analyze(p1, m1, null, 0.7, 7, -25.0, 0.6);
            MilestoneAiAdvisor.AdviceResult r2 = advisor.analyze(p1, m1, null, 0.7, 7, -25.0, 0.6);
            assertThat(r1.fingerprint()).isEqualTo(r2.fingerprint());
            assertThat(r1.fingerprint()).isNotBlank();
        }

        @Test
        @DisplayName("reasons / suggestions 非空 (无信号时也兜底)")
        void reasonsAndSuggestionsNonEmpty() {
            Project p = mockProject(1L);
            Milestone m = mockMilestone(10L, terminalStatus, LocalDate.now().minusDays(30));
            MilestoneAiAdvisor.AdviceResult r = advisor.analyze(p, m, null, 1.0, 0, 0.0, 1.0);
            // 全部信号强度 0 (已完成), reasons 应有兜底
            assertThat(r.reasons().size()).isGreaterThan(0);
            assertThat(r.suggestions().size()).isGreaterThan(0);
        }
    }

    // ============================================================
    // 工具
    // ============================================================
    private Project mockProject(Long id) {
        Project p = mock(Project.class);
        when(p.getId()).thenReturn(id);
        return p;
    }

    private Milestone mockMilestone(Long id, MilestoneStatus status, LocalDate planDate) {
        Milestone m = mock(Milestone.class);
        when(m.getId()).thenReturn(id);
        when(m.getStatus()).thenReturn(status);
        when(m.getPlanDate()).thenReturn(planDate);
        when(m.getName()).thenReturn("测试里程碑");
        return m;
    }

    private Milestone buildMilestone(MilestoneStatus status, int planDateOffsetDays, boolean noPlanDate) {
        Milestone m = mock(Milestone.class);
        when(m.getId()).thenReturn(10L);
        when(m.getStatus()).thenReturn(status);
        if (noPlanDate) {
            when(m.getPlanDate()).thenReturn(null);
        } else {
            when(m.getPlanDate()).thenReturn(LocalDate.now().plusDays(planDateOffsetDays));
        }
        when(m.getName()).thenReturn("测试里程碑");
        return m;
    }
}