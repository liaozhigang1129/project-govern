package com.company.zhiyu.common.security;

import com.company.zhiyu.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 鉴权 Filter。
 *
 *  - access token 走 Authorization: Bearer 头
 *  - 黑名单检查在签名/类型之后,命中 → 401 (code=40102 "revoked")
 *  - 过期 → 401 (code=40101 "expired"),前端收到后自动 /auth/refresh
 *  - 错误统一用 ApiResponse.fail(code, message) 包装
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final RevokedTokenService revokedTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        // P2-B: EventSource 不支持自定义 header,从 ?access_token=... query 取
        String header = req.getHeader("Authorization");
        String token = null;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else if (req.getRequestURI().endsWith("/notifications/stream")) {
            String q = req.getParameter("access_token");
            if (q != null && !q.isBlank()) {
                token = q.trim();
            }
        }
        if (token == null) {
            chain.doFilter(req, res);
            return;
        }
        String username;
        Claims claims;
        try {
            claims = parseClaims(token);
            username = claims.getSubject();
        } catch (ExpiredJwtException ex) {
            writeFail(res, 40101, "token expired");
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            writeFail(res, 40103, "token invalid: " + ex.getMessage());
            return;
        }
        // 类型必须是 access
        Object typ = claims.get("typ", String.class);
        if (!JwtService.TYPE_ACCESS.equals(typ)) {
            writeFail(res, 40103, "wrong token type: " + typ);
            return;
        }
        // 黑名单
        String jti = claims.getId();
        if (revokedTokenService.isRevoked(jti)) {
            writeFail(res, 40102, "token revoked");
            return;
        }
        if (username == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(req, res);
            return;
        }
        UserDetails ud = userDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(req, res);
    }

    private Claims parseClaims(String token) {
        JwtParser p = JwtsParserHolder.PARSER;
        return p.parseSignedClaims(token).getPayload();
    }

    private void writeFail(HttpServletResponse res, int code, String message) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(code, message)));
    }
}
