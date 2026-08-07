package com.company.zhiyu.module.workload;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.security.RequireRoles;
import com.company.zhiyu.module.workload.dto.GanttDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 甘特图 API(P1.5 收尾 + P1.5-d 多部门筛选)
 *  - GET /api/gantt                            自动算范围
 *  - GET /api/gantt?from=...&to=...            显式范围
 *  - GET /api/gantt?pmUserId=3                 按 PM
 *  - GET /api/gantt?departmentIds=1&departmentIds=2  按部门(多选,任一命中)
 *  - GET /api/gantt?includeCompleted=false     隐藏 100% 项目
 */
@RestController
@RequestMapping("/gantt")
@RequiredArgsConstructor
@Tag(name = "Gantt", description = "项目甘特图")
public class GanttController {

    private final GanttService service;

    @GetMapping
    @RequireRoles.Read
    @Operation(summary = "项目甘特图",
        description = "支持多部门筛选(departmentIds 重复参数);不传 = 全部门")
    public ApiResponse<GanttDtos.GanttResponse> gantt(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "departmentIds", required = false) List<Long> departmentIds,
            @RequestParam(required = false) Long pmUserId,
            @RequestParam(required = false) Boolean includeCompleted) {
        return ApiResponse.ok(service.higantt(from, to, departmentIds, pmUserId, includeCompleted));
    }
}