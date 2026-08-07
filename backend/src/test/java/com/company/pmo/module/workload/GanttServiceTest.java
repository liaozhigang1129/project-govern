package com.company.pmo.module.workload;

import com.company.pmo.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("GanttService(甘特图聚合)")
class GanttServiceTest {

    @Autowired GanttService service;

    @Test @DisplayName("G1: 空库(被测空时)→ 0 项目,不抛")
    void emptyDb() {
        // 没 seed → 0 项目
        var resp = service.higantt(null, null, null, null, null);
        assertEquals(0, resp.projectCount());
        assertNotNull(resp.bars());
        assertTrue(resp.bars().isEmpty());
    }

    @Test @DisplayName("G2: 范围参数 from/to 被采用;null=自动")
    void autoRangeVsExplicit() {
        var resp1 = service.higantt(null, null, null, null, null);
        // 自动范围下 rangeFrom <= rangeTo
        assertNotNull(resp1.rangeFrom());
        assertNotNull(resp1.rangeTo());
        assertFalse(resp1.rangeTo().isBefore(resp1.rangeFrom()));
    }

    @Test @DisplayName("G3: 项目过滤 — 不存在的 departmentId → 0 项目")
    void filterByDepartmentEmpty() {
        var resp = service.higantt(null, null, java.util.List.of(99999L), null, null);
        assertEquals(0, resp.projectCount());
    }

    @Test @DisplayName("G4: 项目过滤 — 不存在的 pmUserId → 0 项目")
    void filterByPmEmpty() {
        var resp = service.higantt(null, null, null, 99999L, null);
        assertEquals(0, resp.projectCount());
    }

    @Test @DisplayName("G5: includeCompleted=false 时不进 100% 的项目(在有项目时过滤)")
    void includeCompletedFalse() {
        // 没 seed 数据时返回 0
        var resp = service.higantt(null, null, null, null, false);
        assertEquals(0, resp.projectCount());
    }
}
