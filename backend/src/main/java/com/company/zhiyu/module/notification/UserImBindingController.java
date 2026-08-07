package com.company.zhiyu.module.notification;

import com.company.zhiyu.common.api.ApiResponse;
import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.common.security.RequireRoles;
import com.company.zhiyu.module.notification.dto.UserImBindingDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UserIMBinding 管理 API(P2-A)。
 *
 * RBAC:
 *  - PMO_ADMIN/ADMIN: 查全部、改全部
 *  - 普通用户: 只能查/改自己
 *
 * 注意:此 controller 是 admin/自助绑定入口,所有端点要登录。
 */
@RestController
@RequestMapping("/user-im-bindings")
@RequiredArgsConstructor
public class UserImBindingController {

    private final UserImBindingService service;

    /** 列出自己 / 全部(admin) */
    @GetMapping
    @RequireRoles.Read
    public ApiResponse<List<UserImBindingDtos.View>> list(
            @RequestParam(required = false) Long userId) {
        return ApiResponse.ok(service.list(userId));
    }

    /** 拿一条 */
    @GetMapping("/{id}")
    @RequireRoles.Read
    public ApiResponse<UserImBindingDtos.View> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** 创建(admin) */
    @PostMapping
    @RequireRoles.Admin
    @Transactional
    public ApiResponse<UserImBindingDtos.View> create(@Valid @RequestBody UserImBindingDtos.CreateReq req) {
        return ApiResponse.ok(service.create(req));
    }

    /** 更新(自己 / admin) */
    @PutMapping("/{id}")
    @RequireRoles.Read
    @Transactional
    public ApiResponse<UserImBindingDtos.View> update(@PathVariable Long id,
                                                       @Valid @RequestBody UserImBindingDtos.UpdateReq req) {
        return ApiResponse.ok(service.update(id, req));
    }

    /** 删除(admin) */
    @DeleteMapping("/{id}")
    @RequireRoles.Admin
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
