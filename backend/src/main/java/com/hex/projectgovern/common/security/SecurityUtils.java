package com.hex.projectgovern.common.security;

import com.hex.projectgovern.module.org.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * 当前用户工具(从 SecurityContext 拉 userId)。
 *
 * <p>策略:Authentication.getName() / principal.getUsername() 是字符串,
 * 通过 {@link UserRepository} 反查 userId(走 username)。
 *
 * <p>如果查不到返回 null,不要抛异常(切面 / 异步都可能没认证)。
 *
 * @since 2026-Q1 P1.5-d
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    /** 当前登录用户 ID,未登录返回 null */
    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        final String username;
        Object p = auth.getPrincipal();
        if (p instanceof UserDetails ud) {
            username = ud.getUsername();
        } else if (p instanceof String s) {
            username = s;
        } else {
            username = auth.getName();
        }
        if (username == null || "anonymousUser".equals(username)) return null;
        // 可能本身已经是 userId 字符串(兼容早期 token)
        try {
            Long id = Long.parseLong(username);
            return userRepository.findByIdAndDeletedFalse(id)
                    .map(u -> u.getId())
                    .orElseGet(() -> userRepository.findByUsernameAndDeletedFalse(username)
                            .map(u -> u.getId())
                            .orElse(null));
        } catch (NumberFormatException ignore) {
            // 走 username
        }
        try {
            return userRepository.findByUsernameAndDeletedFalse(username)
                    .map(u -> u.getId())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 当前登录用户名(String),未登录返回 null */
    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth == null) ? null : auth.getName();
    }

    /** 是否有任一角色(空 = 全部 false) */
    public boolean hasAnyRole(String... codes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        for (String c : codes) {
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + c))) {
                return true;
            }
        }
        return false;
    }
}
