package com.hex.projectgovern.module.dashboard.quality;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.security.RequireRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/data-quality")
@RequiredArgsConstructor
@RequireRoles.Read
public class DataQualityController {
    private final DataQualityService service;

    @GetMapping("/snapshot")
    public ApiResponse<Map<String, Object>> snapshot() {
        return ApiResponse.ok(service.snapshot());
    }
}
