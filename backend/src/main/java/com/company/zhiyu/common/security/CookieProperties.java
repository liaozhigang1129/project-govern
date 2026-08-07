package com.company.zhiyu.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Refresh token cookie 配置。
 *
 *  - name:Cookie 名(默认 refreshToken)
 *  - path:Cookie 路径(默认 /api/auth/refresh,只让 refresh 接口读到)
 *  - sameSite:Lax / Strict / None
 *  - httpOnly:固定 true(防 XSS 偷)
 *  - secure:dev=false, prod=true(由 PMO_COOKIE_SECURE 覆盖)
 *  - maxAge:Cookie 寿命(秒),默认 30d
 */
@ConfigurationProperties(prefix = "pmo.security.cookie")
public class CookieProperties {

    private String name = "refreshToken";
    private String path = "/api/auth/refresh";
    private String sameSite = "Lax";
    private boolean httpOnly = true;
    private boolean secure = false;
    private long maxAge = Duration.ofDays(30).toSeconds();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSameSite() { return sameSite; }
    public void setSameSite(String sameSite) { this.sameSite = sameSite; }
    public boolean isHttpOnly() { return httpOnly; }
    public void setHttpOnly(boolean httpOnly) { this.httpOnly = httpOnly; }
    public boolean isSecure() { return secure; }
    public void setSecure(boolean secure) { this.secure = secure; }
    public long getMaxAge() { return maxAge; }
    public void setMaxAge(long maxAge) { this.maxAge = maxAge; }
}
