package com.hex.projectgovern.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(source);
    }
}

/**
 * 限流拦截器注册 (P2 #29 报表导出限流)
 */
@Configuration
class RateLimitWebConfig implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
    private final com.hex.projectgovern.common.ratelimit.RateLimitInterceptor rateLimitInterceptor;
    RateLimitWebConfig(com.hex.projectgovern.common.ratelimit.RateLimitInterceptor r) { this.rateLimitInterceptor = r; }
    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/api/reports/**");
    }
}
