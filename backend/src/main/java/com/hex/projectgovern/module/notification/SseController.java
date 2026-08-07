package com.hex.projectgovern.module.notification;

import com.hex.projectgovern.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * SSE 实时通知推送端点(P2-B)。
 *
 * 客户端使用:
 *   const es = new EventSource('/api/notifications/stream?access_token=...')
 *   es.addEventListener('notification', e => { const n = JSON.parse(e.data); ... })
 *
 * 设计:
 *  - 路径:/notifications/stream
 *  - 需要登录(由 JwtAuthFilter 解析 ?access_token= query,SecurityUtils 取 userId)
 *  - 长连接(SseEmitter 永不过期,用心跳维持)
 *  - 心跳每 25s 一次(代理默认 60s 切断)
 *  - 重连:前端 EventSource 自带重连
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@EnableScheduling
@Slf4j
public class SseController {

    private final SseEmitterRegistry registry;
    private final SecurityUtils securityUtils;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Long userId = securityUtils.currentUserId();
        if (userId == null) {
            // 未登录 → 立即结束 emitter(Spring Security 应该已经拦截,这里兜底)
            SseEmitter e = new SseEmitter();
            e.complete();
            return e;
        }
        log.info("[SSE] open stream userId={}", userId);
        SseEmitter emitter = registry.register(userId);
        // 推一个 "connected" 事件让前端知道握手成功
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("userId", userId, "ts", System.currentTimeMillis())));
        } catch (Exception e) {
            log.debug("[SSE] initial send failed for userId={}: {}", userId, e.getMessage());
        }
        return emitter;
    }

    /** 心跳:每 25s 给所有 emitter 推注释帧(防代理超时) */
    @Scheduled(fixedDelay = 25_000L, initialDelay = 25_000L)
    public void heartbeat() {
        if (registry.totalConnections() > 0) {
            registry.sendHeartbeat();
        }
    }
}
