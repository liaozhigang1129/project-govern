package com.hex.projectgovern.module.alert;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.audit.AuditLog;
import com.hex.projectgovern.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F4: 预警事件 HTTP 入口 (V5.1+ / WP-M5-02 / T-01)
 *
 * <ul>
 *   <li>GET  /api/alerts                  — 分页多条件查询</li>
 *   <li>GET  /api/alerts/{id}             — 详情</li>
 *   <li>POST /api/alerts/{id}/ack         — 确认 (NEW → ACKNOWLEDGED)</li>
 *   <li>POST /api/alerts/{id}/resolve     — 解决 (任意 → RESOLVED)</li>
 *   <li>GET  /api/alerts/stats            — 统计 (按 severity / typeCode)</li>
 * </ul>
 *
 * 权限:PMO_ADMIN / ADMIN (PMO 治理视角)
 */
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE','EXEC')")
public class AlertController {

    private final AlertEventRepository eventRepo;
    private final SecurityUtils securityUtils;

    @GetMapping
    @Operation(summary = "预警列表 (分页 + 多条件)")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size <= 0 || size > 100) size = 20;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size);
        Page<AlertEvent> pg = eventRepo.search(typeCode, severity, status, projectId, pageable);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", pg.getTotalElements());
        data.put("page", pg.getNumber());
        data.put("size", pg.getSize());
        data.put("totalPages", pg.getTotalPages());
        data.put("items", pg.getContent());
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    @Operation(summary = "预警详情")
    public ApiResponse<AlertEvent> get(@PathVariable Long id) {
        return eventRepo.findById(id).map(ApiResponse::ok)
                .orElse(ApiResponse.fail(404, "Alert not found: " + id));
    }

    @PostMapping("/{id}/ack")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "ALERT", action = "ACK")
    @Operation(summary = "确认预警 (NEW → ACKNOWLEDGED)")
    @Transactional
    public ApiResponse<AlertEvent> ack(@PathVariable Long id) {
        var opt = eventRepo.findById(id);
        if (opt.isEmpty()) return ApiResponse.fail(404, "Alert not found: " + id);
        AlertEvent e = opt.get();
        if (!"NEW".equals(e.getStatus())) {
            return ApiResponse.fail(400, "Only NEW alerts can be acknowledged, current=" + e.getStatus());
        }
        e.setStatus("ACKNOWLEDGED");
        e.setAcknowledgedBy(securityUtils.currentUserId());
        e.setAcknowledgedAt(OffsetDateTime.now());
        return ApiResponse.ok(eventRepo.save(e));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN','FINANCE')")
    @AuditLog(module = "ALERT", action = "RESOLVE")
    @Operation(summary = "解决预警 (任意 → RESOLVED)")
    @Transactional
    public ApiResponse<AlertEvent> resolve(@PathVariable Long id) {
        var opt = eventRepo.findById(id);
        if (opt.isEmpty()) return ApiResponse.fail(404, "Alert not found: " + id);
        AlertEvent e = opt.get();
        if ("RESOLVED".equals(e.getStatus())) {
            return ApiResponse.fail(400, "Already RESOLVED");
        }
        e.setStatus("RESOLVED");
        e.setResolvedAt(OffsetDateTime.now());
        return ApiResponse.ok(eventRepo.save(e));
    }

    @GetMapping("/stats")
    @Operation(summary = "预警统计 (按 severity / typeCode)")
    public ApiResponse<Map<String, Object>> stats(
            @RequestParam(required = false) Long projectId) {
        // 简化:不按 projectId 过滤(plan 中说 V5.1 不区分),后续 T-02 可加
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bySeverity", toCountMap(eventRepo.countBySeverityNew()));
        data.put("byTypeCode", toCountMap(eventRepo.countNewByTypeCode()));
        return ApiResponse.ok(data);
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] r : rows) {
            map.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        }
        return map;
    }
}
