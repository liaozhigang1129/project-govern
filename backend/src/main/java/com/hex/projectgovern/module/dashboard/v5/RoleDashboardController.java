package com.hex.projectgovern.module.dashboard.v5;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.security.RequireRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboards/role")
@RequiredArgsConstructor
@RequireRoles.Read
public class RoleDashboardController {
    private final RoleDashboardService service;

    @GetMapping
    public ApiResponse<List<String>> supportedRoles() {
        return ApiResponse.ok(RoleDashboardService.SUPPORTED_ROLES);
    }

    @GetMapping("/{roleCode}")
    public ApiResponse<Map<String, Object>> forRole(
        @PathVariable String roleCode,
        @RequestParam(required = false) Long userId
    ) {
        return ApiResponse.ok(service.forRole(roleCode, userId));
    }
}
