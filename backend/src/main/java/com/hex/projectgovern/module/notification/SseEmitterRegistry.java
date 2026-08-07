package com.hex.projectgovern.module.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * SSE 连接注册表(P2-B 实时推送)。
 *
 * 设计:
 *  - key = userId, value = 该用户的所有 SseEmitter(多 tab/多设备)
 *  - 线程安全: ConcurrentHashMap + CopyOnWriteArraySet
 *  - 推消息时遍历该 userId 的所有 emitter,任一失败就移除
 *  - 不持久化:服务重启后所有连接断开,前端 EventSource 自动重连
 *
 * 上限: 单用户 5 个连接(防滥用,可调)
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    /** userId → 该用户的所有连接 */
    private final ConcurrentHashMap<Long, Set<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    /** 每用户最大连接数(防止一个用户开 100 个 tab 把内存占满) */
    public static final int MAX_EMITTERS_PER_USER = 5;

    /**
     * 注册一个 emitter(用户新开 tab/重连时调)。
     * @return 注册后的 emitter(controller 拿到后返回给前端)
     */
    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = 不过期(用心跳维持)
        Set<SseEmitter> set = userEmitters.computeIfAbsent(userId,
                k -> new CopyOnWriteArraySet<>());
        // 上限保护
        if (set.size() >= MAX_EMITTERS_PER_USER) {
            log.warn("[SSE] userId={} 已达上限 {},关闭最早的 emitter", userId, MAX_EMITTERS_PER_USER);
            SseEmitter oldest = set.iterator().next();
            set.remove(oldest);
            try { oldest.complete(); } catch (Exception ignore) {}
        }
        set.add(emitter);

        // 3 个回调: 清理 + 完成 + 超时
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> { emitter.complete(); });
        emitter.onError(e -> remove(userId, emitter));

        log.info("[SSE] register userId={} count={}", userId, set.size());
        return emitter;
    }

    /** 推一条消息给某用户(所有 emitter 都收到) */
    public void sendToUser(Long userId, Object payload) {
        Set<SseEmitter> set = userEmitters.get(userId);
        if (set == null || set.isEmpty()) return;
        for (SseEmitter em : set) {
            try {
                em.send(SseEmitter.event()
                        .name("notification")
                        .data(payload));
            } catch (IOException e) {
                log.debug("[SSE] sendToUser userId={} failed: {} (will remove)", userId, e.getMessage());
                remove(userId, em);
            } catch (Exception e) {
                log.warn("[SSE] sendToUser userId={} threw: {}", userId, e.getMessage());
                remove(userId, em);
            }
        }
    }

    /** 给所有 emitter 发心跳(防代理超时) */
    public void sendHeartbeat() {
        long ts = System.currentTimeMillis();
        userEmitters.forEach((uid, set) -> {
            for (SseEmitter em : set) {
                try {
                    em.send(SseEmitter.event()
                            .comment("ka-" + ts));
                } catch (Exception e) {
                    log.debug("[SSE] heartbeat userId={} failed: {} (will remove)", uid, e.getMessage());
                    remove(uid, em);
                }
            }
        });
    }

    /** 当前在线连接数(监控/测试用) */
    public int totalConnections() {
        return userEmitters.values().stream().mapToInt(Set::size).sum();
    }

    /** 当前在线用户数 */
    public int onlineUsers() {
        return userEmitters.size();
    }

    private void remove(Long userId, SseEmitter emitter) {
        Set<SseEmitter> set = userEmitters.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) userEmitters.remove(userId);
        }
        try { emitter.complete(); } catch (Exception ignore) {}
    }
}
