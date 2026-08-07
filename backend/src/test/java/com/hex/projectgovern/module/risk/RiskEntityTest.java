package com.hex.projectgovern.module.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Risk Entity 单元测试 (P4)。
 * <p>核心: score / level 由 probability × impact 自动推导, 不接受手动 set 覆盖。
 */
@ExtendWith(MockitoExtension.class)
class RiskEntityTest {

    @Test
    @DisplayName("computeScore: 1×1=1 (最低), 5×5=25 (最高)")
    void computeScore_bounds() {
        assertThat(Risk.computeScore(1, 1)).isEqualTo(1);
        assertThat(Risk.computeScore(5, 5)).isEqualTo(25);
        assertThat(Risk.computeScore(3, 4)).isEqualTo(12);
    }

    @Test
    @DisplayName("computeScore: 越界值会被 clamp 到 1-25")
    void computeScore_clamp() {
        assertThat(Risk.computeScore(0, 0)).isEqualTo(1);    // 下溢 → 1
        assertThat(Risk.computeScore(10, 10)).isEqualTo(25); // 上溢 → 25
    }

    @Test
    @DisplayName("levelOf: 1-4=LOW / 5-9=MEDIUM / 10-15=HIGH / 16-25=CRITICAL")
    void levelOf_thresholds() {
        assertThat(Risk.levelOf(1)).isEqualTo("LOW");
        assertThat(Risk.levelOf(4)).isEqualTo("LOW");
        assertThat(Risk.levelOf(5)).isEqualTo("MEDIUM");
        assertThat(Risk.levelOf(9)).isEqualTo("MEDIUM");
        assertThat(Risk.levelOf(10)).isEqualTo("HIGH");
        assertThat(Risk.levelOf(15)).isEqualTo("HIGH");
        assertThat(Risk.levelOf(16)).isEqualTo("CRITICAL");
        assertThat(Risk.levelOf(25)).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("recomputeScoreAndLevel: 改 probability/impact 后自动同步 score/level")
    void recompute() {
        Risk r = new Risk();
        r.setProjectId(1L);
        r.setCode("R-001");
        r.setTitle("测试");
        r.setCategory("TECHNICAL");
        r.setProbability(2);
        r.setImpact(2);
        // 初始化时 score/level 还是 0/null
        assertThat(r.getScore()).isNull();
        assertThat(r.getLevel()).isNull();

        r.recomputeScoreAndLevel();
        assertThat(r.getScore()).isEqualTo(4);
        assertThat(r.getLevel()).isEqualTo("LOW");

        // 改成高风险
        r.setProbability(5);
        r.setImpact(5);
        r.recomputeScoreAndLevel();
        assertThat(r.getScore()).isEqualTo(25);
        assertThat(r.getLevel()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("recomputeScoreAndLevel: probability/impact 为 null 时不抛错也不改")
    void recompute_nullSafe() {
        Risk r = new Risk();
        r.setProbability(null);
        r.setImpact(null);
        r.setScore(99);   // 保持旧值
        r.setLevel("HIGH");
        r.recomputeScoreAndLevel();
        assertThat(r.getScore()).isEqualTo(99);
        assertThat(r.getLevel()).isEqualTo("HIGH");
    }
}
