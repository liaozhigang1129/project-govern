package com.hex.projectgovern.common.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Refresh token cookie 助手。
 *
 * Cookie 路径限制在 /api/auth/refresh,浏览器只在请求该路径时附带 cookie,
 * 减小被 JS 直接读取的窗口(httpOnly 已经防护了一层)。
 */
@Component
public class RefreshTokenCookieFactory {

    private final CookieProperties props;

    public RefreshTokenCookieFactory(CookieProperties props) {
        this.props = props;
    }

    /** 下发 refreshToken cookie */
    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(props.getName(), refreshToken)
                .httpOnly(props.isHttpOnly())
                .secure(props.isSecure())
                .sameSite(props.getSameSite())
                .path(props.getPath())
                .maxAge(Duration.ofSeconds(props.getMaxAge()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 清除 cookie(登出时调用) */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(props.getName(), "")
                .httpOnly(props.isHttpOnly())
                .secure(props.isSecure())
                .sameSite(props.getSameSite())
                .path(props.getPath())
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public CookieProperties getProps() {
        return props;
    }
}
