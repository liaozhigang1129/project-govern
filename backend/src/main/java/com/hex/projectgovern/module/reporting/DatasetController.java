package com.hex.projectgovern.module.reporting;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.security.RequireRoles;
import com.hex.projectgovern.module.reporting.dto.ReportingDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
@RequireRoles.Read
public class DatasetController {
    private final DatasetService service;

    @GetMapping
    public ApiResponse<List<DatasetResponse>> list(
        @RequestParam(required = false) String domain,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(service.list(domain, status).stream()
            .map(d -> new DatasetResponse(
                d.getId(), d.getCode(), d.getName(), d.getDomain(),
                d.getSourceTable(), d.getStatus(), d.getRefreshPolicy(),
                d.getDescription()
            )).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<DatasetResponse> get(@PathVariable Long id) {
        var d = service.get(id);
        return ApiResponse.ok(new DatasetResponse(
            d.getId(), d.getCode(), d.getName(), d.getDomain(),
            d.getSourceTable(), d.getStatus(), d.getRefreshPolicy(),
            d.getDescription()
        ));
    }

    @PostMapping
    public ApiResponse<DatasetResponse> create(@Valid @RequestBody DatasetRequest req) {
        var d = service.create(req, null);
        return ApiResponse.ok(new DatasetResponse(
            d.getId(), d.getCode(), d.getName(), d.getDomain(),
            d.getSourceTable(), d.getStatus(), d.getRefreshPolicy(),
            d.getDescription()
        ));
    }

    @PatchMapping("/{id}")
    public ApiResponse<DatasetResponse> update(@PathVariable Long id, @Valid @RequestBody DatasetRequest req) {
        var d = service.update(id, req);
        return ApiResponse.ok(new DatasetResponse(
            d.getId(), d.getCode(), d.getName(), d.getDomain(),
            d.getSourceTable(), d.getStatus(), d.getRefreshPolicy(),
            d.getDescription()
        ));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<String> testSql(@PathVariable Long id) {
        return ApiResponse.ok(service.testSql(id));
    }

    @GetMapping("/{id}/fields")
    public ApiResponse<List<DatasetFieldResponse>> listFields(@PathVariable Long id) {
        return ApiResponse.ok(service.listFields(id).stream()
            .map(f -> new DatasetFieldResponse(
                f.getId(), f.getFieldName(), f.getDisplayName(),
                f.getFieldType(), f.getDataType(), f.getAggFunc(), f.getDimRole()
            )).toList());
    }

    @PostMapping("/{id}/fields")
    public ApiResponse<DatasetFieldResponse> addField(@PathVariable Long id, @Valid @RequestBody DatasetFieldRequest req) {
        var req2 = new DatasetFieldRequest(id, req.fieldName(), req.displayName(),
            req.fieldType(), req.dataType(), req.aggFunc(), req.formula(), req.dimRole(), req.sortOrder());
        var f = service.addField(req2);
        return ApiResponse.ok(new DatasetFieldResponse(
            f.getId(), f.getFieldName(), f.getDisplayName(),
            f.getFieldType(), f.getDataType(), f.getAggFunc(), f.getDimRole()
        ));
    }
}
