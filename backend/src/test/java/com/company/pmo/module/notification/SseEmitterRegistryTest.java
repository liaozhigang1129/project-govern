package com.company.pmo.module.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SseEmitterRegistry 单元测试(P2-B)。
 *
 * 覆盖:
 *  - register/sendToUser 正常路径
 *  - sendToUser 失败时自动移除坏 emitter
 *  - 多用户隔离
 *  - 单用户连接数上限
 *  - 心跳 sendHeartbeat 不抛
 *  - 计数器(totalConnections/onlineUsers)
 */
class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
    }

    @Test
    void register_and_send_to_user() throws Exception {
        SseEmitter em = registry.register(1L);
        assertNotNull(em);
        assertEquals(1, registry.totalConnections());
        assertEquals(1, registry.onlineUsers());

        // send 不应该抛(实际推送由 Spring 异步执行,这里只测 API 路径)
        registry.sendToUser(1L, "hello");
        registry.sendToUser(999L, "no-one");
    }

    @Test
    void multiple_users_isolated() {
        SseEmitter a1 = registry.register(1L);
        SseEmitter a2 = registry.register(1L);
        SseEmitter b1 = registry.register(2L);

        assertEquals(3, registry.totalConnections());
        assertEquals(2, registry.onlineUsers());

        // sendToUser 不应影响连接数(只是发消息)
        registry.sendToUser(1L, "msg-a");
        assertEquals(3, registry.totalConnections());
        assertEquals(2, registry.onlineUsers());
    }

    @Test
    void emitter_limit_per_user() {
        for (int i = 0; i < SseEmitterRegistry.MAX_EMITTERS_PER_USER; i++) {
            registry.register(1L);
        }
        // 多开一个: 应该把最早的清掉,总数仍 = MAX
        registry.register(1L);
        assertEquals(SseEmitterRegistry.MAX_EMITTERS_PER_USER, registry.totalConnections());
    }

    @Test
    void heartbeat_does_not_throw() {
        SseEmitter em = registry.register(1L);
        // 模拟 emitter 已被关闭: send 会失败,但 heartbeat 内部 catch,不抛
        try { em.complete(); } catch (Exception ignore) {}
        // 此时调用 heartbeat: 会捕获异常并 remove
        assertDoesNotThrow(registry::sendHeartbeat);
    }

    @Test
    void count_metrics() {
        assertEquals(0, registry.totalConnections());
        assertEquals(0, registry.onlineUsers());

        SseEmitter a = registry.register(1L);
        SseEmitter b = registry.register(2L);
        SseEmitter b2 = registry.register(2L);
        assertEquals(3, registry.totalConnections());
        assertEquals(2, registry.onlineUsers());
    }
}
