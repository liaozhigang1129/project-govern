package com.company.pmo.module.org;

import com.company.pmo.common.api.ApiResponse;
import com.company.pmo.common.audit.AuditLog;
import com.company.pmo.common.security.JwtService;
import com.company.pmo.common.security.RefreshTokenCookieFactory;
import com.company.pmo.common.security.RevokedTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth", description = "JWT 登录 & 当前用户(2h access + 30d refresh 双 token)")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenCookieFactory cookieFactory;
    private final RevokedTokenService revokedTokenService;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    @PostMapping("/login")
    @AuditLog(module = "AUTH", action = "LOGIN", extractResourceId = false)
    @Operation(summary = "登录返回 accessToken (body) + Set-Cookie refreshToken (HttpOnly)")
    public ApiResponse<Map<String, Object>> login(
            @RequestBody LoginRequest req,
            HttpServletResponse response) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        UserDetails ud = (UserDetails) auth.getPrincipal();
        AppUser u = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();
        // P1.5-c: token subject 保持 username,filter 才能 authenticate 后续请求
        // P1.5-d: 切面需要 userId,SecurityUtils.currentUserId() 走 userRepository 反查
        String accessToken = jwtService.generateAccessToken(u.getUsername());
        String refreshToken = jwtService.generateRefreshToken(u.getUsername());
        // dev/curl 也能拿到 refreshToken(无浏览器时),生产前端用 cookie
        cookieFactory.setRefreshTokenCookie(response, refreshToken);
        return ApiResponse.ok(toLoginData(accessToken, refreshToken, u));
    }

    @PostMapping("/refresh")
    @AuditLog(module = "AUTH", action = "REFRESH", extractResourceId = false)
    @Operation(summary = "用 refreshToken 换新 accessToken(并轮换 refreshToken)")
    public ApiResponse<Map<String, Object>> refresh(
            @RequestBody(required = false) RefreshRequest body,  // 优先 body,否则读 cookie
            @CookieValue(name = "refreshToken", required = false) String cookieToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        // 1) 取 token:body 优先(兼容 dev/curl),fallback cookie
        String oldRefresh = (body != null && body.refreshToken() != null)
                ? body.refreshToken()
                : cookieToken;
        if (oldRefresh == null || oldRefresh.isBlank()) {
            return ApiResponse.fail(401, "refresh token missing");
        }
        // 2) 校验类型 + 未过期
        if (!jwtService.isTokenValid(oldRefresh, null, JwtService.TYPE_REFRESH)) {
            return ApiResponse.fail(401, "refresh token invalid or expired");
        }
        // 3) 黑名单
        if (revokedTokenService.isRevoked(jwtService.extractJti(oldRefresh))) {
            return ApiResponse.fail(401, "refresh token revoked");
        }
        // 4) 拉黑旧的 + 发新的(轮换)
        String username = jwtService.extractUsername(oldRefresh);
        Instant exp = Instant.now().plusMillis(jwtService.getRefreshExpirationMs());
        revokedTokenService.revoke(jwtService.extractJti(oldRefresh), lookupUserId(username), exp);

        String newAccess = jwtService.generateAccessToken(username);
        String newRefresh = jwtService.generateRefreshToken(username);
        cookieFactory.setRefreshTokenCookie(response, newRefresh);

        AppUser u = userRepository.findByUsernameAndDeletedFalse(username).orElseThrow();
        return ApiResponse.ok(toLoginData(newAccess, newRefresh, u));
    }

    @PostMapping("/logout")
    @AuditLog(module = "AUTH", action = "LOGOUT", extractResourceId = false)
    @Operation(summary = "登出:把当前 accessToken 写黑名单 + 清 cookie")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @CookieValue(name = "refreshToken", required = false) String cookieToken,
            HttpServletResponse response) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            try {
                if (jwtService.isTokenValid(accessToken, null, JwtService.TYPE_ACCESS)) {
                    String jti = jwtService.extractJti(accessToken);
                    String username = jwtService.extractUsername(accessToken);
                    Instant exp = Instant.now().plusMillis(jwtService.getAccessExpirationMs());
                    revokedTokenService.revoke(jti, lookupUserId(username), exp);
                }
            } catch (Exception e) {
                log.debug("logout: invalid access token, skip revoke: {}", e.getMessage());
            }
        }
        if (cookieToken != null) {
            try {
                if (jwtService.isTokenValid(cookieToken, null, JwtService.TYPE_REFRESH)) {
                    String jti = jwtService.extractJti(cookieToken);
                    String username = jwtService.extractUsername(cookieToken);
                    Instant exp = Instant.now().plusMillis(jwtService.getRefreshExpirationMs());
                    revokedTokenService.revoke(jti, lookupUserId(username), exp);
                }
            } catch (Exception e) {
                log.debug("logout: invalid refresh token, skip revoke: {}", e.getMessage());
            }
        }
        cookieFactory.clearRefreshTokenCookie(response);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    @Operation(summary = "当前用户信息")
    public ApiResponse<AppUser> me(@AuthenticationPrincipal UserDetails ud) {
        return ApiResponse.ok(userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow());
    }

    /**
     * 当前登录用户的全部角色 ID (L1-3 配套: 前端拿这个去 /api/role-menus/mine 查可见菜单)
     *  返回 { roleIds:[1,2,3], primaryRoleId:1, primaryRoleCode:"PMO_ADMIN" }
     */
    @GetMapping("/me/roles")
    @Operation(summary = "当前用户角色 ID 列表 (含主角色 + 兼任)")
    public ApiResponse<Map<String, Object>> myRoles(@AuthenticationPrincipal UserDetails ud) {
        AppUser u = userRepository.findByUsernameAndDeletedFalse(ud.getUsername()).orElseThrow();
        List<Long> ids = userRoleRepository.findRoleIdsByUserId(u.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roleIds", ids);
        data.put("primaryRoleId", u.getPrimaryRole() == null ? null : u.getPrimaryRole().getId());
        data.put("primaryRoleCode", u.getPrimaryRole() == null ? null : u.getPrimaryRole().getCode());
        return ApiResponse.ok(data);
    }

    // ====== 内部工具 ======

    private Map<String, Object> toLoginData(String accessToken, String refreshToken, AppUser u) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("user", Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "fullName", u.getFullName(),
                "role", u.getPrimaryRole().getCode(),
                "departmentId", u.getDepartmentId() == null ? -1 : u.getDepartmentId()
        ));
        return data;
    }

    private Long lookupUserId(String username) {
        return userRepository.findByUsernameAndDeletedFalse(username)
                .map(AppUser::getId)
                .orElse(-1L);
    }
}
