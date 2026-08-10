package com.hex.projectgovern.module.alert;

import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.common.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AlertController 单测 (WP-M5-02 / T-01)
 *
 * 5 端点:
 *  - GET  /api/alerts               — 列表
 *  - GET  /api/alerts/{id}          — 详情
 *  - POST /api/alerts/{id}/ack      — 确认
 *  - POST /api/alerts/{id}/resolve  — 解决
 *  - GET  /api/alerts/stats         — 统计
 */
class AlertControllerTest {

    private AlertEventRepository eventRepo;
    private SecurityUtils securityUtils;
    private AlertController controller;

    @BeforeEach
    void setUp() {
        eventRepo = mock(AlertEventRepository.class);
        securityUtils = mock(SecurityUtils.class);
        controller = new AlertController(eventRepo, securityUtils);
    }

    private static <T> T withId(T obj, Long id) {
        try {
            Field f = obj.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(obj, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return obj;
    }

    private AlertEvent event(Long id, String status, String severity) {
        AlertEvent e = new AlertEvent();
        e.setRuleId(1L);
        e.setSeverity(severity);
        e.setMessage("test alert");
        e.setTargetType("PROJECT");
        e.setTargetId(100L);
        e.setProjectId(100L);
        e.setStatus(status);
        e.setNotifyStatus("PENDING");
        return withId(e, id);
    }

    // ============================================================
    // 列表
    // ============================================================

    @Test
    @DisplayName("列表: 多条件 + 分页 + size 越界 clamp")
    void list_basic() {
        Page<AlertEvent> pg = new PageImpl<>(List.of(event(1L, "NEW", "HIGH")), PageRequest.of(0, 20), 1);
        when(eventRepo.search(eq("COST_DIFF"), eq("HIGH"), eq("NEW"), eq(100L), any(Pageable.class)))
            .thenReturn(pg);

        var resp = controller.list("COST_DIFF", "HIGH", "NEW", 100L, 0, 20);

        assertThat(resp.getCode()).isEqualTo(0);
        Map<String, Object> data = resp.getData();
        assertThat(data.get("total")).isEqualTo(1L);
        assertThat(data.get("page")).isEqualTo(0);
        assertThat(data.get("size")).isEqualTo(20);
        assertThat((List<?>) data.get("items")).hasSize(1);
    }

    @Test
    @DisplayName("列表: size=300 → clamp 到 20")
    void list_sizeClamp() {
        when(eventRepo.search(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        var resp = controller.list(null, null, null, null, 0, 300);

        assertThat(resp.getCode()).isEqualTo(0);
        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(eventRepo).search(any(), any(), any(), any(), cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(20);
    }

    // ============================================================
    // 详情
    // ============================================================

    @Test
    @DisplayName("详情: 存在 → 返回")
    void get_found() {
        AlertEvent e = event(7L, "NEW", "HIGH");
        when(eventRepo.findById(7L)).thenReturn(java.util.Optional.of(e));

        var resp = controller.get(7L);

        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData().getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("详情: 不存在 → 404")
    void get_notFound() {
        when(eventRepo.findById(999L)).thenReturn(java.util.Optional.empty());

        var resp = controller.get(999L);

        assertThat(resp.getCode()).isEqualTo(404);
    }

    // ============================================================
    // ack
    // ============================================================

    @Test
    @DisplayName("ack: NEW → ACKNOWLEDGED, 设置操作人 + 时间")
    void ack_success() {
        AlertEvent e = event(10L, "NEW", "HIGH");
        when(eventRepo.findById(10L)).thenReturn(java.util.Optional.of(e));
        when(securityUtils.currentUserId()).thenReturn(42L);
        when(eventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = controller.ack(10L);

        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(e.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(e.getAcknowledgedBy()).isEqualTo(42L);
        assertThat(e.getAcknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("ack: 非 NEW 状态 → 400")
    void ack_invalidStatus() {
        AlertEvent e = event(11L, "RESOLVED", "HIGH");
        when(eventRepo.findById(11L)).thenReturn(java.util.Optional.of(e));

        var resp = controller.ack(11L);

        assertThat(resp.getCode()).isEqualTo(400);
        assertThat(resp.getMessage()).contains("Only NEW");
    }

    @Test
    @DisplayName("ack: 不存在 → 404")
    void ack_notFound() {
        when(eventRepo.findById(anyLong())).thenReturn(java.util.Optional.empty());

        var resp = controller.ack(999L);

        assertThat(resp.getCode()).isEqualTo(404);
    }

    // ============================================================
    // resolve
    // ============================================================

    @Test
    @DisplayName("resolve: NEW → RESOLVED, 设置 resolvedAt")
    void resolve_success() {
        AlertEvent e = event(20L, "NEW", "MEDIUM");
        when(eventRepo.findById(20L)).thenReturn(java.util.Optional.of(e));
        when(eventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = controller.resolve(20L);

        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(e.getStatus()).isEqualTo("RESOLVED");
        assertThat(e.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("resolve: 已是 RESOLVED → 400")
    void resolve_alreadyResolved() {
        AlertEvent e = event(21L, "RESOLVED", "MEDIUM");
        when(eventRepo.findById(21L)).thenReturn(java.util.Optional.of(e));

        var resp = controller.resolve(21L);

        assertThat(resp.getCode()).isEqualTo(400);
        assertThat(resp.getMessage()).contains("Already RESOLVED");
    }

    // ============================================================
    // stats
    // ============================================================

    @Test
    @DisplayName("stats: 按 severity + typeCode 聚合")
    void stats() {
        when(eventRepo.countBySeverityNew()).thenReturn(List.of(
                new Object[]{"HIGH", 5L},
                new Object[]{"MEDIUM", 12L}));
        when(eventRepo.countNewByTypeCode()).thenReturn(List.of(
                new Object[]{"COST_DIFF", 3L},
                new Object[]{"BUDGET_EXCEED", 2L}));

        var resp = controller.stats(null);

        assertThat(resp.getCode()).isEqualTo(0);
        Map<String, Object> data = resp.getData();
        @SuppressWarnings("unchecked")
        Map<String, Long> sev = (Map<String, Long>) data.get("bySeverity");
        @SuppressWarnings("unchecked")
        Map<String, Long> tc = (Map<String, Long>) data.get("byTypeCode");
        assertThat(sev).containsEntry("HIGH", 5L).containsEntry("MEDIUM", 12L);
        assertThat(tc).containsEntry("COST_DIFF", 3L).containsEntry("BUDGET_EXCEED", 2L);
    }

    @Test
    @DisplayName("stats: 空数据 → 空 map")
    void stats_empty() {
        when(eventRepo.countBySeverityNew()).thenReturn(List.of());
        when(eventRepo.countNewByTypeCode()).thenReturn(List.of());

        var resp = controller.stats(null);

        assertThat(resp.getCode()).isEqualTo(0);
        @SuppressWarnings("unchecked")
        Map<String, Long> sev = (Map<String, Long>) resp.getData().get("bySeverity");
        assertThat(sev).isEmpty();
    }
}
