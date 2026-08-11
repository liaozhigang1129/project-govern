package com.hex.projectgovern.module.reporting;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.common.security.RequireRoles;
import com.hex.projectgovern.module.reporting.dto.ReportingDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@RequireRoles.Read
public class ReportSubscriptionController {
    private final ReportSubscriptionService service;

    @GetMapping
    public ApiResponse<List<SubscriptionResponse>> list(@RequestParam(required = false) Long userId) {
        return ApiResponse.ok(service.listByUser(userId == null ? 0L : userId).stream()
            .map(s -> new SubscriptionResponse(
                s.getId(), s.getCode(), s.getUserId(), s.getChannelSet(),
                s.getCron(), s.getStatus()
            )).toList());
    }

    @PostMapping
    public ApiResponse<SubscriptionResponse> create(@Valid @RequestBody SubscriptionRequest req) {
        var s = service.create(req);
        return ApiResponse.ok(new SubscriptionResponse(
            s.getId(), s.getCode(), s.getUserId(), s.getChannelSet(),
            s.getCron(), s.getStatus()
        ));
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<SubscriptionResponse> pause(@PathVariable Long id) {
        var s = service.pause(id);
        return ApiResponse.ok(new SubscriptionResponse(
            s.getId(), s.getCode(), s.getUserId(), s.getChannelSet(),
            s.getCron(), s.getStatus()
        ));
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<SubscriptionResponse> resume(@PathVariable Long id) {
        var s = service.resume(id);
        return ApiResponse.ok(new SubscriptionResponse(
            s.getId(), s.getCode(), s.getUserId(), s.getChannelSet(),
            s.getCron(), s.getStatus()
        ));
    }

    @PostMapping("/{id}/run")
    public ApiResponse<SubscriptionResponse> runNow(@PathVariable Long id) {
        var s = service.triggerNow(id);
        return ApiResponse.ok(new SubscriptionResponse(
            s.getId(), s.getCode(), s.getUserId(), s.getChannelSet(),
            s.getCron(), s.getStatus()
        ));
    }
}
