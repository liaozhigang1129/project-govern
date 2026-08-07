package com.company.pmo.module.notification;

import com.company.pmo.common.api.ApiResponse;
import com.company.pmo.common.security.RequireRoles;
import com.company.pmo.module.notification.dto.UserImQuietHoursDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DND 勿扰时段 API(P2 #2)。
 *
 * RBAC:
 *  - 所有端点要登录(@RequireRoles.Read)
 *  - 业务上由前端自助管理(每用户只查自己)— service 层不做隔离,
 *    因为后续 PMO_ADMIN 可能要为某用户代配(本版本先开放)
 */
@RestController
@RequestMapping("/user-im-quiet-hours")
@RequiredArgsConstructor
public class UserImQuietHoursController {

    private final UserImQuietHoursService service;

    @GetMapping
    @RequireRoles.Read
    public ApiResponse<List<UserImQuietHoursDtos.View>> list(@RequestParam Long userId) {
        return ApiResponse.ok(service.list(userId));
    }

    @PostMapping
    @RequireRoles.Read
    @Transactional
    public ApiResponse<UserImQuietHoursDtos.View> create(@Valid @RequestBody UserImQuietHoursDtos.CreateReq req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @RequireRoles.Read
    @Transactional
    public ApiResponse<UserImQuietHoursDtos.View> update(@PathVariable Long id,
                                                          @Valid @RequestBody UserImQuietHoursDtos.UpdateReq req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @RequireRoles.Read
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
