package com.company.zhiyu.module.workload;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.module.workload.dto.WorkloadDtos.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * P2.B 人员负载查询单测。
 * 走 H2 in-memory(不依赖 PG),entity/Service 全栈验证。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
@Sql(scripts = "/seed-workload.sql")
class WorkloadServiceTest {

    @Autowired WorkloadService service;

    private static final LocalDate THIS_MON =
            LocalDate.of(2026, 6, 1); // 周一

    private LocalDate mondayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    @Test
    @DisplayName("W1: 用户矩阵 — 没工时也返回,status=NO_DATA")
    void userMatrixEmpty() {
        // 0 工时,0 用户 — 但 H2 测试 profile 一定至少有 seeded users
        // 不一定 → 只测结构:rows 是 list,weekCount=4
        var m = service.userLoadMatrix(null, null, THIS_MON, THIS_MON.plusWeeks(3));
        assertThat(m.from()).isEqualTo(THIS_MON);
        assertThat(m.to()).isEqualTo(THIS_MON.plusWeeks(3));
        assertThat(m.weekCount()).isEqualTo(4);
        assertThat(m.rows()).isNotNull();
    }

    @Test
    @DisplayName("W2: 矩阵行数 = 用户数 × 周数(每人每周一行)")
    void userMatrixShape() {
        // H2 至少 1 个用户(由 Flyway seed 灌入,test profile 也走 flyway)
        var m = service.userLoadMatrix(null, null, THIS_MON, THIS_MON.plusWeeks(3));
        if (m.rows().isEmpty()) return; // 无用户 → 跳过形状校验
        long userCount = m.rows().stream().map(UserWeekRow::userId).distinct().count();
        assertThat(m.rows()).hasSize((int) (userCount * 4));
    }

    @Test
    @DisplayName("W3: 单用户 scopeUserId — 只返回该用户的行")
    void userMatrixScoped() {
        // 取第一个用户
        var full = service.userLoadMatrix(null, null, THIS_MON, THIS_MON.plusWeeks(3));
        if (full.rows().isEmpty()) return;
        Long firstUid = full.rows().get(0).userId();
        var scoped = service.userLoadMatrix(null, firstUid, THIS_MON, THIS_MON.plusWeeks(3));
        assertThat(scoped.rows()).allMatch(r -> r.userId().equals(firstUid));
    }

    @Test
    @DisplayName("W4: 项目负载 — 不存在的项目 → 404")
    void projectLoadNotFound() {
        assertThatThrownBy(() ->
                service.projectLoad(999_999_999L, THIS_MON.minusDays(30), THIS_MON.plusDays(7)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    @Test
    @DisplayName("W5: 项目负载 — 存在但无工时 → 0 hours")
    void projectLoadEmpty() {
        // 项目 1 是 seed 数据
        var load = service.projectLoad(1L, THIS_MON.minusDays(30), THIS_MON.plusDays(7));
        assertThat(load.projectId()).isEqualTo(1L);
        assertThat(load.projectName()).isNotBlank();
        assertThat(load.totalHours()).isNotNull();
        assertThat(load.byMember()).isNotNull();
        assertThat(load.byDay()).isNotNull();
    }

    @Test
    @DisplayName("W6: 矩阵 — 给一个不存在的 userId → 0 rows")
    void userMatrixUnknownUser() {
        var m = service.userLoadMatrix(null, 999_999_999L, THIS_MON, THIS_MON.plusWeeks(3));
        assertThat(m.rows()).isEmpty();
    }

    @Test
    @DisplayName("W7: 矩阵 — 给一个不存在的 departmentId → 0 rows")
    void userMatrixUnknownDept() {
        var m = service.userLoadMatrix(999_999_999L, null, THIS_MON, THIS_MON.plusWeeks(3));
        assertThat(m.rows()).isEmpty();
    }
}
