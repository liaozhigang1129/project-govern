package com.company.zhiyu.module.admin;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.audit.AuditLog;
import com.company.zhiyu.common.security.RequireRoles;
import com.company.zhiyu.module.admin.dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin / 系统参数")
@RestController
@RequestMapping("/admin/system-config")
@RequiredArgsConstructor
public class SystemConfigAdminController {

    private final SystemConfigService service;

    @GetMapping
    @RequireRoles.Read
    public ApiResponse<List<ConfigListItem>> list(
            @RequestParam(required = false) String group) {
        var all = group == null ? service.listAll() : service.listByGroup(group);
        return ApiResponse.ok(all.stream().map(ConfigListItem::from).toList());
    }

    @GetMapping("/groups")
    @RequireRoles.Read
    public ApiResponse<Map<String, Long>> listGroups() {
        var all = service.listAll();
        Map<String, Long> m = new java.util.LinkedHashMap<>();
        for (var c : all) m.merge(c.getConfigGroup(), 1L, Long::sum);
        return ApiResponse.ok(m);
    }

    @GetMapping("/{key}")
    @RequireRoles.Read
    public ApiResponse<ConfigListItem> getOne(@PathVariable String key) {
        var c = service.getByKey(key);
        if (c == null) throw new com.company.zhiyu.common.exception.BusinessException(404, "config not found: " + key);
        return ApiResponse.ok(ConfigListItem.from(c));
    }

    @PutMapping("/{key}")
    @RequireRoles.Admin
    @AuditLog(module = "SYS_CONFIG", action = "UPDATE")
    public ApiResponse<ConfigListItem> update(
            @PathVariable String key,
            @RequestBody ConfigUpdateRequest req) {
        var c = service.upsert(key, req.configValue());
        return ApiResponse.ok(ConfigListItem.from(c));
    }

    @PostMapping("/batch-update")
    @RequireRoles.Admin
    @AuditLog(module = "SYS_CONFIG", action = "BATCH_UPDATE")
    public ApiResponse<Integer> batchUpdate(@RequestBody ConfigBatchUpdateRequest req) {
        int n = 0;
        for (var item : req.items()) {
            service.upsert(item.configKey(), item.configValue());
            n++;
        }
        return ApiResponse.ok(n);
    }

    @PostMapping("/{key}/reset")
    @RequireRoles.Admin
    @AuditLog(module = "SYS_CONFIG", action = "RESET")
    public ApiResponse<ConfigListItem> reset(@PathVariable String key) {
        service.resetToDefault(key);
        return getOne(key);
    }

    @PostMapping("/cache/evict")
    @RequireRoles.Admin
    @AuditLog(module = "SYS_CONFIG", action = "EVICT_CACHE")
    public ApiResponse<Void> evict() {
        service.evictCache();
        return ApiResponse.ok(null);
    }
}
