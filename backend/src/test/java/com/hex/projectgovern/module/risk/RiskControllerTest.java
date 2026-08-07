package com.hex.projectgovern.module.risk;

import com.hex.projectgovern.module.risk.dto.RiskRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RiskController MockMvc 切片测试 (P4)。
 * <p>只测 HTTP 路由 + 序列化, 业务逻辑在 RiskServiceTest 测。
 */
@WebMvcTest(controllers = RiskController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class RiskControllerTest {

    @Autowired MockMvc mvc;
    @MockBean RiskService riskService;
    // WebMvcTest 切片要 mock 掉所有 security/audit Bean 才能加载 Context
    @MockBean com.hex.projectgovern.common.security.JwtAuthFilter jwtAuthFilter;
    @MockBean com.hex.projectgovern.common.security.JwtService jwtService;
    @MockBean com.hex.projectgovern.common.security.SecurityUtils securityUtils;
    @MockBean com.hex.projectgovern.common.audit.OperationLogAspect operationLogAspect;

    @Test
    @DisplayName("GET /risks/by-project/3 → 200 + 数组")
    void listByProject_returns200() throws Exception {
        when(riskService.listByProject(3L)).thenReturn(List.of());

        mvc.perform(get("/risks/by-project/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /risks/by-project/3/active → 200 + 数组")
    void listActive_returns200() throws Exception {
        when(riskService.listActiveByProject(3L)).thenReturn(List.of());

        mvc.perform(get("/risks/by-project/3/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /risks/{id} → 200")
    void getById_returns200() throws Exception {
        var r = new com.hex.projectgovern.module.risk.dto.RiskResponse(
                10L, 3L, "R-001", "测试", null, "TECHNICAL",
                4, 5, 20, "CRITICAL", "OPEN", null, null,
                null, null, null, LocalDate.now(), null, null,
                null, null, null, null, null, null, null);
        when(riskService.getById(10L)).thenReturn(r);

        mvc.perform(get("/risks/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.code").value("R-001"))
                .andExpect(jsonPath("$.data.score").value(20))
                .andExpect(jsonPath("$.data.level").value("CRITICAL"));
    }

    @Test
    @DisplayName("POST /risks 新建 → 200 + 响应")
    void create_returns200() throws Exception {
        var r = new com.hex.projectgovern.module.risk.dto.RiskResponse(
                1L, 3L, "R-001", "x", null, "TECHNICAL",
                3, 3, 9, "HIGH", "OPEN", null, null,
                null, null, null, LocalDate.now(), null, null,
                null, null, null, null, null, null, null);
        when(riskService.save(any(RiskRequest.class), any())).thenReturn(r);

        mvc.perform(post("/risks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {
                            "projectId": 3,
                            "code": "R-001",
                            "title": "x",
                            "category": "TECHNICAL",
                            "probability": 3,
                            "impact": 3
                          }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("R-001"))
                .andExpect(jsonPath("$.data.score").value(9));
    }

    @Test
    @DisplayName("DELETE /risks/{id} → 200")
    void delete_returns200() throws Exception {
        mvc.perform(delete("/risks/1"))
                .andExpect(status().isOk());
        verify(riskService).softDelete(eq(1L), any());
    }

    @Test
    @DisplayName("GET /risks/health/by-project/3 → 200 + KPI")
    void health_returns200() throws Exception {
        var s = new com.hex.projectgovern.module.risk.dto.RiskHealthSummary(
                3L, 5L, 3L, 1L, 1L, 0L, 20,
                java.util.Map.of("TECHNICAL", 2L),
                java.util.Map.of("HIGH", 1L, "CRITICAL", 1L));
        when(riskService.healthSummary(3L)).thenReturn(s);

        mvc.perform(get("/risks/health/by-project/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(5))
                .andExpect(jsonPath("$.data.activeCount").value(3))
                .andExpect(jsonPath("$.data.criticalActive").value(1))
                .andExpect(jsonPath("$.data.maxActiveScore").value(20));
    }

    @Test
    @DisplayName("GET /risks/matrix/by-project/3 → 200 + 25 cells")
    void matrix_returns200() throws Exception {
        var cells = new java.util.ArrayList<com.hex.projectgovern.module.risk.dto.RiskMatrix.Cell>();
        for (int p = 1; p <= 5; p++) {
            for (int i = 1; i <= 5; i++) {
                cells.add(new com.hex.projectgovern.module.risk.dto.RiskMatrix.Cell(p, i, 0, List.of()));
            }
        }
        when(riskService.matrix(3L)).thenReturn(new com.hex.projectgovern.module.risk.dto.RiskMatrix.Matrix(cells));

        mvc.perform(get("/risks/matrix/by-project/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cells").isArray())
                .andExpect(jsonPath("$.data.cells.length()").value(25));
    }
}
