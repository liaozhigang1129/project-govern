package com.hex.projectgovern.module.org;

import com.hex.projectgovern.common.security.RequireRoles;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Component;

/**
 * @deprecated 由 {@link UserAdminController} 替代 (L1-1 用户管理)。
 *             保留此空类仅为防止老 import 编译失败, 不再注册任何端点。
 */
@Component
@RequireRoles.Read
@Tag(name = "Users (deprecated)", description = "已迁移到 /users 由 UserAdminController 提供")
@Deprecated
public class UserController {
    // 旧 GET /users 端点已由 UserAdminController#list() 取代
}
