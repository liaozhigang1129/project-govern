package com.hex.projectgovern.module.reporting;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.security.RequireRoles;
import com.hex.projectgovern.module.reporting.dto.ReportingDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@RequireRoles.Read
public class ReportTemplateController {
    private final ReportTemplateService service;

    @GetMapping
    public ApiResponse<List<ReportTemplateResponse>> list(@RequestParam(required = false) String category) {
        return ApiResponse.ok(service.list(category).stream()
            .map(t -> new ReportTemplateResponse(
                t.getId(), t.getCode(), t.getCategory(), t.getName(),
                t.getFormat(), t.getStatus(), t.getScheduleCron()
            )).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<ReportTemplateResponse> get(@PathVariable Long id) {
        var t = service.get(id);
        return ApiResponse.ok(new ReportTemplateResponse(
            t.getId(), t.getCode(), t.getCategory(), t.getName(),
            t.getFormat(), t.getStatus(), t.getScheduleCron()
        ));
    }

    @PostMapping
    public ApiResponse<ReportTemplateResponse> create(@Valid @RequestBody ReportTemplateRequest req) {
        var t = service.create(req, null);
        return ApiResponse.ok(new ReportTemplateResponse(
            t.getId(), t.getCode(), t.getCategory(), t.getName(),
            t.getFormat(), t.getStatus(), t.getScheduleCron()
        ));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ReportTemplateResponse> publish(@PathVariable Long id) {
        var t = service.publish(id);
        return ApiResponse.ok(new ReportTemplateResponse(
            t.getId(), t.getCode(), t.getCategory(), t.getName(),
            t.getFormat(), t.getStatus(), t.getScheduleCron()
        ));
    }

    @PostMapping("/{id}/render")
    public ApiResponse<Object> render(@PathVariable Long id, @RequestBody(required = false) ReportRenderRequest req) {
        return ApiResponse.ok(service.render(id));
    }
}
