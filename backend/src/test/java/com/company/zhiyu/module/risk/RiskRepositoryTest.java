package com.company.zhiyu.module.risk;

import com.company.zhiyu.module.risk.dto.RiskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RiskRepository JPA 集成测试 (P4)。
 * <p>用 H2 + Flyway-off 模式, 依赖 Hibernate ddl-auto 自动建表。
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
class RiskRepositoryTest {

    @Autowired RiskRepository riskRepository;
    @Autowired RiskHistoryRepository historyRepository;
    @Autowired RiskResponseRepository responseRepository;

    @Test
    @DisplayName("save 新建: id 自增, score/level 由 entity 自己算")
    void save_persistsWithComputedScore() {
        Risk r = new Risk();
        r.setProjectId(1L);
        r.setCode("R-001");
        r.setTitle("服务器宕机");
        r.setCategory("EXTERNAL");
        r.setProbability(3);
        r.setImpact(4);
        r.recomputeScoreAndLevel();
        Risk saved = riskRepository.save(r);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getScore()).isEqualTo(12);
        assertThat(saved.getLevel()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc: 按 score 降序")
    void findByProject_orderByScoreDesc() {
        Risk r1 = mkRisk(1L, "R-001", 1, 1);   // score=1
        Risk r2 = mkRisk(1L, "R-002", 4, 5);   // score=20
        Risk r3 = mkRisk(1L, "R-003", 3, 3);   // score=9
        riskRepository.saveAll(List.of(r1, r2, r3));

        List<Risk> result = riskRepository.findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(1L);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getScore()).isEqualTo(20);
        assertThat(result.get(1).getScore()).isEqualTo(9);
        assertThat(result.get(2).getScore()).isEqualTo(1);
    }

    @Test
    @DisplayName("findActiveByProject: 排除 CLOSED/ACCEPTED")
    void findActive_excludesClosed() {
        Risk open = mkRisk(1L, "R-001", 3, 3);
        open.setStatus("OPEN");
        Risk closed = mkRisk(1L, "R-002", 4, 4);
        closed.setStatus("CLOSED");
        Risk accepted = mkRisk(1L, "R-003", 5, 5);
        accepted.setStatus("ACCEPTED");
        riskRepository.saveAll(List.of(open, closed, accepted));

        List<Risk> active = riskRepository.findActiveByProject(1L);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getCode()).isEqualTo("R-001");
    }

    @Test
    @DisplayName("countByProjectIdAndCodeAndDeletedFalse: 唯一性检查")
    void countByProjectAndCode() {
        riskRepository.save(mkRisk(1L, "R-001", 3, 3));
        assertThat(riskRepository.countByProjectIdAndCodeAndDeletedFalse(1L, "R-001")).isEqualTo(1L);
        assertThat(riskRepository.countByProjectIdAndCodeAndDeletedFalse(1L, "R-999")).isEqualTo(0L);
    }

    @Test
    @DisplayName("aggregateHealth: 一次返回 6 个 KPI")
    void aggregateHealth_returns6Fields() {
        Risk open = mkRisk(1L, "R-001", 5, 5);    // CRITICAL, score=25
        open.setStatus("OPEN");
        Risk mig = mkRisk(1L, "R-002", 4, 3);     // HIGH, score=12
        mig.setStatus("MITIGATING");
        Risk occ = mkRisk(1L, "R-003", 2, 2);     // LOW, score=4
        occ.setStatus("OCCURRED");
        Risk closed = mkRisk(1L, "R-004", 5, 5);  // CRITICAL 已关闭, 不算 active
        closed.setStatus("CLOSED");
        riskRepository.saveAll(List.of(open, mig, occ, closed));

        // JPQL 多列查询在 H2 实际返回 Object[][] (外层数组每行一结果), 而非单行 Object[]
        // PG 也是 Object[], 但单 SELECT 单结果; Hibernate 在多列 SELECT 时把单行包成 Object[] of Object[]
        // 这里加一层 unwrap: 取第一行
        Object[] row = (Object[]) ((Object[]) riskRepository.aggregateHealth(1L))[0];
        // 0: total, 1: active, 2: criticalActive, 3: highActive, 4: occurred, 5: maxActiveScore
        // H2 JPQL COUNT 返回 Long, MAX 返回 Long; 安全起见用 Number 包一层
        assertThat(((Number) row[0]).longValue()).isEqualTo(4L);  // 4 个
        assertThat(((Number) row[1]).longValue()).isEqualTo(3L);  // 3 个 active
        assertThat(((Number) row[2]).longValue()).isEqualTo(1L);  // 1 critical active
        assertThat(((Number) row[3]).longValue()).isEqualTo(1L);  // 1 high active
        assertThat(((Number) row[4]).longValue()).isEqualTo(1L);  // 1 occurred
        // MAX 表达式: H2 返回 Long 而非 Integer, 用 longValue 防 ClassCast
        assertThat(((Number) row[5]).longValue()).isEqualTo(25L); // max active score
    }

    @Test
    @DisplayName("findActiveById: 软删后查不到")
    void findActiveById_excludesSoftDeleted() {
        Risk r = mkRisk(1L, "R-001", 3, 3);
        Risk saved = riskRepository.save(r);
        saved.setDeleted(true);
        riskRepository.save(saved);

        assertThat(riskRepository.findActiveById(saved.getId())).isEmpty();
    }

    private static Risk mkRisk(Long projectId, String code, int p, int i) {
        Risk r = new Risk();
        r.setProjectId(projectId);
        r.setCode(code);
        r.setTitle("t");
        r.setCategory("TECHNICAL");
        r.setProbability(p);
        r.setImpact(i);
        r.recomputeScoreAndLevel();
        return r;
    }
}
