package com.hex.projectgovern.module.finance;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.common.security.SecurityUtils;
import com.hex.projectgovern.module.finance.dto.FinanceDtos.ReconciliationDto;
import com.hex.projectgovern.module.finance.dto.FinanceDtos.ReconciliationHealth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * FinanceController 对账 4 端点 单测 (WP-M4-03 / T-05)
 *
 * 直接调用 controller 方法,绕过 Spring Web 上下文,验证:
 *  - listReconciliation 多条件 + 分页 + status 校验
 *  - getReconciliation 404 处理
 *  - retryReconciliation 调 service + 操作人传递
 *  - reconciliationHealth 聚合字段映射
 */
class FinanceReconciliationControllerTest {

    private ReconciliationService reconciliationService;
    private SecurityUtils securityUtils;
    private FinanceController controller;

    @BeforeEach
    void setUp() {
        reconciliationService = mock(ReconciliationService.class);
        securityUtils = mock(SecurityUtils.class);
        // 其余 service 不在本测试范围,mock 即可
        controller = new FinanceController(
                mock(ContractService.class),
                mock(InvoiceService.class),
                mock(PaymentService.class),
                mock(CostItemService.class),
                securityUtils,
                reconciliationService);
    }

    // ============================================================
    // 列表
    // ============================================================

    @Test
    @DisplayName("列表: 无 status, size=20, page=0, 排序 reconciledAt DESC")
    void list_basic() {
        Page<ReconciliationDto> pg = new PageImpl<>(List.of(sampleDto(1L)), PageRequest.of(0, 20), 1);
        when(reconciliationService.search(eq(100L), eq(null), eq(null), eq(null), any(Pageable.class)))
            .thenReturn(pg);

        var resp = controller.listReconciliation(100L, null, null, null, 0, 20);

        assertThat(resp.getCode()).isEqualTo(0);
        Map<String, Object> data = resp.getData();
        assertThat(data.get("total")).isEqualTo(1L);
        assertThat(data.get("page")).isEqualTo(0);
        assertThat(data.get("size")).isEqualTo(20);
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) data.get("items");
        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("列表: status=MISMATCH 解析 + 传递")
    void list_statusFilter() {
        Page<ReconciliationDto> pg = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(reconciliationService.search(any(), eq(CostReconciliation.MatchStatus.MISMATCH),
                any(), any(), any(Pageable.class))).thenReturn(pg);

        var resp = controller.listReconciliation(null, "mismatch", null, null, 0, 20);

        assertThat(resp.getCode()).isEqualTo(0);
        verify(reconciliationService).search(any(), eq(CostReconciliation.MatchStatus.MISMATCH),
                any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("列表: status 非法 → 400")
    void list_invalidStatus() {
        var resp = controller.listReconciliation(null, "NOT_A_STATUS", null, null, 0, 20);

        assertThat(resp.getCode()).isEqualTo(400);
        assertThat(resp.getMessage()).contains("Invalid status").contains("NOT_A_STATUS");
        verify(reconciliationService, never()).search(any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("列表: size 越界 (300) → clamp 到 20")
    void list_sizeClamp() {
        when(reconciliationService.search(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        var resp = controller.listReconciliation(null, null, null, null, 0, 300);

        assertThat(resp.getCode()).isEqualTo(0);
        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(reconciliationService).search(any(), any(), any(), any(), cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(20);
    }

    // ============================================================
    // 详情
    // ============================================================

    @Test
    @DisplayName("详情: 存在 → 返回 dto")
    void get_found() {
        ReconciliationDto dto = sampleDto(7L);
        when(reconciliationService.get(7L)).thenReturn(dto);

        var resp = controller.getReconciliation(7L);

        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData().id()).isEqualTo(7L);
    }

    @Test
    @DisplayName("详情: 不存在 → 404")
    void get_notFound() {
        when(reconciliationService.get(999L)).thenReturn(null);

        var resp = controller.getReconciliation(999L);

        assertThat(resp.getCode()).isEqualTo(404);
    }

    // ============================================================
    // 重试
    // ============================================================

    @Test
    @DisplayName("重试: 传入 operator userId")
    void retry_withOperator() {
        ReconciliationDto dto = sampleDto(11L);
        when(securityUtils.currentUserId()).thenReturn(42L);
        when(reconciliationService.retry(11L, 42L)).thenReturn(dto);

        var resp = controller.retryReconciliation(11L);

        assertThat(resp.getCode()).isEqualTo(0);
        verify(reconciliationService).retry(11L, 42L);
    }

    @Test
    @DisplayName("重试: service 抛 NOT_FOUND → 透传")
    void retry_notFoundPropagates() {
        when(securityUtils.currentUserId()).thenReturn(1L);
        when(reconciliationService.retry(anyLong(), anyLong()))
            .thenThrow(new BusinessException("RECONCILIATION_NOT_FOUND", "not found"));

        try {
            controller.retryReconciliation(99L);
        } catch (BusinessException e) {
            assertThat(e.getErrorCode()).isEqualTo("RECONCILIATION_NOT_FOUND");
        }
    }

    // ============================================================
    // 健康度
    // ============================================================

    @Test
    @DisplayName("健康度: 返回 total/matched/mismatch/partial/pending/totalDiff/greenRate")
    void health_full() {
        ReconciliationHealth h = new ReconciliationHealth(
                10L, 8L, 1L, 1L, 0L, new BigDecimal("500.00"));
        when(reconciliationService.health(100L)).thenReturn(h);

        var resp = controller.reconciliationHealth(100L);

        assertThat(resp.getCode()).isEqualTo(0);
        Map<String, Object> data = resp.getData();
        assertThat(data.get("projectId")).isEqualTo(100L);
        assertThat(data.get("total")).isEqualTo(10L);
        assertThat(data.get("matched")).isEqualTo(8L);
        assertThat(data.get("mismatch")).isEqualTo(1L);
        assertThat(data.get("partial")).isEqualTo(1L);
        assertThat(data.get("pending")).isEqualTo(0L);
        assertThat(data.get("totalDiff")).isEqualTo(new BigDecimal("500.00"));
        assertThat(data.get("greenRate")).isEqualTo(0.8);
    }

    @Test
    @DisplayName("健康度: projectId=null → 全公司")
    void health_global() {
        when(reconciliationService.health(null)).thenReturn(ReconciliationHealth.empty());

        var resp = controller.reconciliationHealth(null);

        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData().get("greenRate")).isEqualTo(1.0);
    }

    // ============================================================
    // 工具
    // ============================================================

    private ReconciliationDto sampleDto(Long id) {
        return new ReconciliationDto(id, 100L, 1L, 2L, 3L, 4L, "2026-08",
                new BigDecimal("10000.00"), new BigDecimal("10000.00"),
                new BigDecimal("10000.00"), new BigDecimal("10000.00"),
                BigDecimal.ZERO, "ok",
                CostReconciliation.MatchStatus.MATCHED, Instant.now(), 1L);
    }
}
