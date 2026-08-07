package com.hex.projectgovern.module.resourcepipeline;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.security.RequireRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** P6 资源管道大盘 REST */
@RestController
@RequestMapping("/resource-pipeline")
@RequiredArgsConstructor
@RequireRoles.Read
@Tag(name = "ResourcePipeline", description = "资源管理协同大盘")
public class ResourcePipelineController {

    private final ResourcePipelineService service;

    @GetMapping("/kpis")
    public ApiResponse<Map<String, Object>> kpis() {
        return ApiResponse.ok(service.kpis());
    }

    @GetMapping("/capacity-matrix")
    public ApiResponse<Map<String, Object>> capacityMatrix(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.ok(service.capacityMatrix(from, to));
    }

    @GetMapping("/skill-matrix")
    public ApiResponse<List<Map<String, Object>>> skillMatrix() {
        return ApiResponse.ok(service.skillMatrix());
    }

    @GetMapping("/overload-alerts")
    public ApiResponse<List<Map<String, Object>>> overloadAlerts() {
        return ApiResponse.ok(service.overloadAlerts());
    }

    @GetMapping("/department-capacity")
    public ApiResponse<List<Map<String, Object>>> deptCapacity() {
        return ApiResponse.ok(service.deptCapacity());
    }
}
