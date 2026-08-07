package com.company.pmo.common.audit;

import com.company.pmo.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 操作审计查询接口(限 PMO/ADMIN 角色)。
 *
 * <p>GET /api/audit-logs
 * <ul>
 *   <li>支持按 resourceType / userId / action / 时间范围 多条件分页</li>
 *   <li>默认窗口:近 7 天(可用 start / end 覆盖)</li>
 *   <li>默认 size=20,max=100,page 从 0 开始</li>
 *   <li>排序:createdAt DESC</li>
 * </ul>
 *
 * <p>GET /api/audit-logs/{id}
 * <ul>
 *   <li>详情:返回完整 payload JSON 原文</li>
 * </ul>
 *
 * @since 2026-Q1 P1.5-d
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PMO_ADMIN','ADMIN')")
@Tag(name = "AuditLog", description = "操作审计查询(PMO/ADMIN 限读)")
public class AuditLogController {

    private final OperationLogRepository repository;

    /** 默认窗口天数 */
    private static final int DEFAULT_DAYS = 7;
    /** 默认 page size */
    private static final int DEFAULT_SIZE = 20;
    /** 最大 page size(防止 DBA 被打) */
    private static final int MAX_SIZE = 100;

    @GetMapping
    @Operation(summary = "审计列表(分页 + 多条件)")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size) {
        // 默认窗口
        Instant endInst = end != null ? end : Instant.now();
        Instant startInst = start != null ? start : endInst.minus(DEFAULT_DAYS, ChronoUnit.DAYS);
        // size 限位
        if (size <= 0) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
        if (page < 0) page = 0;

        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OperationLog> pg = repository.search(resourceType, userId, action, startInst, endInst, pr);

        List<Map<String, Object>> items = pg.getContent().stream()
                .map(this::toListItem)
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", pg.getTotalElements());
        data.put("page", pg.getNumber());
        data.put("size", pg.getSize());
        data.put("totalPages", pg.getTotalPages());
        data.put("start", startInst);
        data.put("end", endInst);
        data.put("items", items);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    @Operation(summary = "审计详情(含完整 payload)")
    public ApiResponse<OperationLog> get(@PathVariable Long id) {
        return ApiResponse.ok(repository.findById(id).orElse(null));
    }

    /** 列表摘要(不返回完整 payload,改由详情接口看) */
    private Map<String, Object> toListItem(OperationLog o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("userId", o.getUserId());
        m.put("resourceType", o.getResourceType());
        m.put("resourceId", o.getResourceId());
        m.put("action", o.getAction());
        m.put("ipAddress", o.getIpAddress());
        m.put("createdAt", o.getCreatedAt());
        // payload 摘要:从 JSON 里取 result
        if (o.getPayload() != null) {
            try {
                int idx = o.getPayload().indexOf("\"result\"");
                if (idx > 0) {
                    int colon = o.getPayload().indexOf(':', idx);
                    int quote1 = o.getPayload().indexOf('"', colon);
                    int quote2 = o.getPayload().indexOf('"', quote1 + 1);
                    if (quote1 > 0 && quote2 > quote1) {
                        m.put("result", o.getPayload().substring(quote1 + 1, quote2));
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return m;
    }
}
