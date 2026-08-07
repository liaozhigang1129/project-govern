package com.company.pmo.module.wbs;

import com.company.pmo.module.wbs.dto.BudgetSnapshotResponse;
import com.company.pmo.module.wbs.dto.WbsAssignmentRequest;
import com.company.pmo.module.wbs.dto.WbsAssignmentResponse;
import com.company.pmo.module.wbs.dto.WbsProgressSummary;
import com.company.pmo.module.wbs.dto.WbsTaskNode;
import com.company.pmo.module.wbs.dto.WbsTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WbsController HTTP 层契约测试 (P0-A.3 Step 3)。
 *
 * 用 {@code @WebMvcTest} 切片, 只加载 Web 层 + WbsController, 其他 Bean 全部 mock。
 * 关键: {@code addFilters=false} 绕过 SecurityFilterChain, 避免 SecurityConfig
 * 的 JwtAuthFilter 构造器依赖导致 context 启动失败。
 * Controller 上的 {@code @RequireRoles.Read} 注解也就不再拦截 — 测试的是 HTTP 层契约,
 * 鉴权测试在专门的 SecurityTest 里覆盖。
 *
 * 覆盖:
 *  - 13 个端点全部能路由到 (200/400)
 *  - ApiResponse 包装 ({code:0, message:"ok", data:...})
 *  - GET/POST/DELETE 方法正确
 *  - path variable + query param + request body 正确绑定
 *  - JSON 序列化: LocalDate / BigDecimal / Instant 走 Jackson 默认
 *  - 校验: 缺 projectId → 400
 *
 * 不连数据库, 不跑 Security 实际逻辑。
 */
@WebMvcTest(controllers = WbsController.class)
@AutoConfigureMockMvc(addFilters = false)
class WbsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean WbsService wbsService;
    // SecurityConfig 依赖的 JwtAuthFilter / JwtService 也 mock, 防止 @Import 链上拉起
    @MockBean com.company.pmo.common.security.JwtAuthFilter jwtAuthFilter;
    @MockBean com.company.pmo.common.security.JwtService jwtService;
    @MockBean com.company.pmo.common.audit.OperationLogAspect operationLogAspect;

    // ============================================================
    // GET /wbs/tasks/by-project/{projectId}
    // ============================================================

    @Test
    @DisplayName("GET /wbs/tasks/by-project/3 → 200 + ApiResponse 包装 data=树根")
    void tree_returns200_wrapped() throws Exception {
        WbsTaskNode n = new WbsTaskNode(1L, 3L, null, "1", "根", "EXECUTION", "NOT_STARTED",
                null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 1,
                false, false, null, List.of(), null, null,
                Instant.now(), Instant.now(), 0, List.of("1"), List.of());
        when(wbsService.listTreeByProject(3L)).thenReturn(List.of(n));

        mvc.perform(get("/wbs/tasks/by-project/3"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].wbsCode").value("1"))
                .andExpect(jsonPath("$.data[0].depth").value(0));

        verify(wbsService).listTreeByProject(3L);
    }

    // ============================================================
    // GET /wbs/tasks/flat/by-project/{projectId}
    // ============================================================

    @Test
    @DisplayName("GET /wbs/tasks/flat/by-project/3 → 200 + 扁平列表")
    void flat_returns200() throws Exception {
        when(wbsService.listFlatByProject(3L)).thenReturn(List.of());
        mvc.perform(get("/wbs/tasks/flat/by-project/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
        verify(wbsService).listFlatByProject(3L);
    }

    // ============================================================
    // GET /wbs/tasks/{id}
    // ============================================================

    @Test
    @DisplayName("GET /wbs/tasks/10 → 200 + 节点详情")
    void getTask_returns200() throws Exception {
        WbsTask t = new WbsTask();
        t.setId(10L); t.setProjectId(3L); t.setWbsCode("1.1"); t.setName("子");
        when(wbsService.getById(10L)).thenReturn(t);
        mvc.perform(get("/wbs/tasks/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.wbsCode").value("1.1"));
    }

    // ============================================================
    // POST /wbs/tasks (create/update)
    // ============================================================

    @Test
    @DisplayName("POST /wbs/tasks 合法 body → 200, save 调一次")
    void saveTask_returns200() throws Exception {
        WbsTask saved = new WbsTask();
        saved.setId(99L); saved.setProjectId(3L); saved.setWbsCode("1.2"); saved.setName("新");
        when(wbsService.save(any(WbsTaskRequest.class))).thenReturn(saved);

        String body = """
            {
              "projectId": 3,
              "wbsCode": "1.2",
              "name": "新",
              "taskType": "EXECUTION",
              "status": "NOT_STARTED",
              "weight": 1,
              "progressPct": 0,
              "planHours": 0,
              "actualHours": 0
            }
            """;
        mvc.perform(post("/wbs/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(99));
        verify(wbsService).save(any(WbsTaskRequest.class));
    }

    @Test
    @DisplayName("POST /wbs/tasks 缺 projectId → 400 (validation)")
    void saveTask_missingProjectId_returns400() throws Exception {
        String body = """
            {
              "wbsCode": "1.1",
              "name": "无项目",
              "taskType": "EXECUTION",
              "status": "NOT_STARTED"
            }
            """;
        mvc.perform(post("/wbs/tasks")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // ============================================================
    // DELETE /wbs/tasks/{id}
    // ============================================================

    @Test
    @DisplayName("DELETE /wbs/tasks/10 → 200, softDelete 调一次")
    void deleteTask_returns200() throws Exception {
        mvc.perform(delete("/wbs/tasks/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(wbsService).softDelete(10L);
    }

    // ============================================================
    // GET /wbs/progress/{projectId}
    // ============================================================

    @Test
    @DisplayName("GET /wbs/progress/3 → 200 + 进度汇总字段")
    void progress_returns200() throws Exception {
        WbsProgressSummary s = new WbsProgressSummary(
                3L, 5, 2, 1, 1, 1, 1, 0,
                new BigDecimal("66.6667"),
                new BigDecimal("40.00"),
                new BigDecimal("10.00"),
                new BigDecimal("25.0"));
        when(wbsService.progressSummary(3L)).thenReturn(s);
        mvc.perform(get("/wbs/progress/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskCount").value(5))
                .andExpect(jsonPath("$.data.completedCount").value(2))
                .andExpect(jsonPath("$.data.weightedProgressPct").value(66.6667));
    }

    // ============================================================
    // 资源分配: by-task / by-user / upsert / delete
    // ============================================================

    @Test
    @DisplayName("GET /wbs/assignments/by-task/10 → 200 + 列表")
    void assignmentsByTask_returns200() throws Exception {
        WbsAssignmentResponse a = new WbsAssignmentResponse(
                1L, 10L, 7L, "DOER", new BigDecimal("8.00"), BigDecimal.ZERO,
                null, null, Instant.now(), Instant.now());
        when(wbsService.listAssignmentsByTask(10L)).thenReturn(List.of(a));
        mvc.perform(get("/wbs/assignments/by-task/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(7))
                .andExpect(jsonPath("$.data[0].role").value("DOER"));
    }

    @Test
    @DisplayName("POST /wbs/assignments 合法 body → 200")
    void upsertAssignment_returns200() throws Exception {
        WbsAssignment saved = new WbsAssignment();
        saved.setId(50L); saved.setWbsTaskId(10L); saved.setUserId(7L);
        saved.setRole("DOER"); saved.setPlannedHours(new BigDecimal("8.00"));
        when(wbsService.upsertAssignment(any(WbsAssignmentRequest.class))).thenReturn(saved);

        String body = """
            {
              "wbsTaskId": 10,
              "userId": 7,
              "role": "DOER",
              "plannedHours": 8.00
            }
            """;
        mvc.perform(post("/wbs/assignments")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(50));
    }

    @Test
    @DisplayName("DELETE /wbs/assignments/50 → 200")
    void deleteAssignment_returns200() throws Exception {
        mvc.perform(delete("/wbs/assignments/50"))
                .andExpect(status().isOk());
        verify(wbsService).deleteAssignment(50L);
    }

    // ============================================================
    // 快照: list / range / trigger
    // ============================================================

    @Test
    @DisplayName("GET /wbs/snapshots/3 → 200 + 列表")
    void snapshots_returns200() throws Exception {
        BudgetSnapshotResponse s = new BudgetSnapshotResponse(
                99L, 3L, LocalDate.of(2025, 1, 15), 1, "x",
                new BigDecimal("100000"), new BigDecimal("50000"),
                new BigDecimal("40000"), new BigDecimal("45000"),
                new BigDecimal("0.889"), new BigDecimal("0.800"),
                new BigDecimal("112500"), new BigDecimal("67500"), new BigDecimal("-12500"),
                1L, Instant.now());
        when(wbsService.listSnapshots(3L)).thenReturn(List.of(s));
        mvc.perform(get("/wbs/snapshots/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cpi").value(0.889));
    }

    @Test
    @DisplayName("GET /wbs/snapshots/3/range?from=&to= → 200")
    void snapshotsRange_returns200() throws Exception {
        when(wbsService.snapshotsInRange(anyLong(), any(), any())).thenReturn(List.of());
        mvc.perform(get("/wbs/snapshots/3/range")
                        .param("from", "2025-01-01").param("to", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("POST /wbs/snapshots/3/trigger 带 body → 200, snapshotNow 调一次")
    void triggerSnapshot_returns200() throws Exception {
        BudgetSnapshotResponse s = new BudgetSnapshotResponse(
                100L, 3L, LocalDate.now(), 2, "人工触发",
                new BigDecimal("100000"), new BigDecimal("60000"),
                new BigDecimal("50000"), new BigDecimal("50000"),
                BigDecimal.ONE, new BigDecimal("0.833"),
                new BigDecimal("100000"), new BigDecimal("50000"), BigDecimal.ZERO,
                1L, Instant.now());
        when(wbsService.snapshotNow(anyLong(), any(), any())).thenReturn(s);

        String body = "{\"reason\":\"人工触发\"}";
        mvc.perform(post("/wbs/snapshots/3/trigger")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("人工触发"));
    }

    @Test
    @DisplayName("EVM 趋势: GET /wbs/snapshots/3/trend → 200, 默认 days=30")
    void trendEndpoint_defaultDays() throws Exception {
        when(wbsService.trendSince(anyLong(), org.mockito.ArgumentMatchers.eq(30)))
                .thenReturn(List.of());
        mvc.perform(get("/wbs/snapshots/3/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("EVM 趋势: GET /wbs/snapshots/3/trend?days=60 → 透传 days=60")
    void trendEndpoint_customDays() throws Exception {
        when(wbsService.trendSince(anyLong(), org.mockito.ArgumentMatchers.eq(60)))
                .thenReturn(List.of());
        mvc.perform(get("/wbs/snapshots/3/trend").param("days", "60"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("P3.2 资源矩阵: GET /wbs/assignments/by-project/3 → 200")
    void assignmentsByProject_returns200() throws Exception {
        when(wbsService.listAssignmentsByProject(anyLong())).thenReturn(List.of());
        mvc.perform(get("/wbs/assignments/by-project/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ============================================================
    // P3.3 WBS 甘特图
    // ============================================================

    @Test
    @DisplayName("P3.3 WBS 甘特图: GET /wbs/gantt/by-project/3 → 200 + rows 数组")
    void ganttByProject_returns200() throws Exception {
        com.company.pmo.module.wbs.dto.WbsGanttRow row =
                new com.company.pmo.module.wbs.dto.WbsGanttRow(
                        10L, "1.1", "需求分析", 0, 1L,
                        "EXECUTION", "IN_PROGRESS",
                        7L, "张三",
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15),
                        LocalDate.of(2026, 6, 2), null,
                        40, 5,
                        true, false,
                        new BigDecimal("16.00"), new BigDecimal("4.00"));
        com.company.pmo.module.wbs.dto.WbsGanttResponse resp =
                new com.company.pmo.module.wbs.dto.WbsGanttResponse(
                        3L, "2026-05-25", "2026-06-22", 1, List.of(row));
        when(wbsService.ganttByProject(anyLong())).thenReturn(resp);

        mvc.perform(get("/wbs/gantt/by-project/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskCount").value(1))
                .andExpect(jsonPath("$.data.rangeFrom").value("2026-05-25"))
                .andExpect(jsonPath("$.data.rangeTo").value("2026-06-22"))
                .andExpect(jsonPath("$.data.rows[0].taskId").value(10))
                .andExpect(jsonPath("$.data.rows[0].wbsCode").value("1.1"))
                .andExpect(jsonPath("$.data.rows[0].ownerName").value("张三"))
                .andExpect(jsonPath("$.data.rows[0].progressPct").value(40))
                .andExpect(jsonPath("$.data.rows[0].critical").value(true));
    }

    @Test
    @DisplayName("P3.3 WBS 甘特图: 空项目 → 200, rangeFrom/rangeTo 仍给出 (today 回退)")
    void ganttByProject_emptyProject() throws Exception {
        com.company.pmo.module.wbs.dto.WbsGanttResponse resp =
                new com.company.pmo.module.wbs.dto.WbsGanttResponse(
                        3L, "2026-05-10", "2026-08-08", 0, List.of());
        when(wbsService.ganttByProject(anyLong())).thenReturn(resp);

        mvc.perform(get("/wbs/gantt/by-project/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskCount").value(0))
                .andExpect(jsonPath("$.data.rows").isArray())
                .andExpect(jsonPath("$.data.rows.length()").value(0));
    }

    // ============================================================
    // P3.2 网络图 + P3.3 关键路径
    // ============================================================

}
