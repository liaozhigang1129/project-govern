package com.company.pmo.module.admin;

import com.company.pmo.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 系统参数服务
 * - DB 持久化 + 内存 Caffeine 缓存 (60s)
 * - 业务代码统一通过 getXxx(key, default) 读,修改后自动失效
 * - 提供类型转换 (STRING/NUMBER/BOOLEAN/JSON/ENUM)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigRepository repo;
    private final ObjectMapper objectMapper;

    // 内存缓存: key -> SystemConfig (含 value + type + 默认)
    private final Map<String, SystemConfig> cache = new ConcurrentHashMap<>();
    private volatile long cacheLoadedAt = 0;
    private static final long CACHE_TTL_MS = 60_000L;

    private static final Pattern NUMBER_RE = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");

    // ==================== 业务读取 ====================

    public SystemConfig getByKey(String key) {
        ensureLoaded();
        return cache.get(key);
    }

    public String getString(String key, String defaultValue) {
        var c = getByKey(key);
        return c == null ? defaultValue : c.getConfigValue();
    }

    public int getInt(String key, int defaultValue) {
        var s = getString(key, null);
        if (s == null || s.isBlank()) return defaultValue;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return defaultValue; }
    }

    public long getLong(String key, long defaultValue) {
        var s = getString(key, null);
        if (s == null || s.isBlank()) return defaultValue;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return defaultValue; }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        var s = getString(key, null);
        if (s == null) return defaultValue;
        return Boolean.parseBoolean(s.trim()) || "1".equals(s.trim());
    }

    public <T> T getJson(String key, TypeReference<T> ref, T defaultValue) {
        var s = getString(key, null);
        if (s == null || s.isBlank()) return defaultValue;
        try { return objectMapper.readValue(s, ref); }
        catch (Exception e) { return defaultValue; }
    }

    public List<SystemConfig> listAll() {
        ensureLoaded();
        return List.copyOf(cache.values());
    }

    public List<SystemConfig> listByGroup(String group) {
        return listAll().stream()
                .filter(c -> group.equals(c.getConfigGroup()))
                .toList();
    }

    // ==================== 修改 ====================

    @Transactional
    public SystemConfig upsert(String key, String newValue) {
        var c = repo.findByConfigKey(key).orElseThrow(
            () -> new BusinessException(404, "config not found: " + key));
        // 类型校验
        validateValue(c.getValueType(), newValue, c.getOptions());
        c.setConfigValue(newValue);
        c.setUpdatedAt(Instant.now());
        var saved = repo.save(c);
        evictCache();
        log.info("[SysConfig] {} updated by ops, type={}, new length={}",
            key, saved.getValueType(), newValue == null ? 0 : newValue.length());
        return saved;
    }

    @Transactional
    public void resetToDefault(String key) {
        var c = repo.findByConfigKey(key).orElseThrow(
            () -> new BusinessException(404, "config not found: " + key));
        c.setConfigValue(c.getDefaultValue());
        c.setUpdatedAt(Instant.now());
        repo.save(c);
        evictCache();
        log.info("[SysConfig] {} reset to default", key);
    }

    // ==================== 内部 ====================

    private void validateValue(SystemConfigValueType t, String v, String opts) {
        if (v == null) throw new BusinessException(400, "值不能为空");
        switch (t) {
            case NUMBER -> { if (!NUMBER_RE.matcher(v.trim()).matches()) throw new BusinessException(400, "必须是数字: " + v); }
            case BOOLEAN -> { if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false") && !v.equals("1") && !v.equals("0")) throw new BusinessException(400, "必须是 boolean: " + v); }
            case JSON -> { try { objectMapper.readTree(v); } catch (Exception e) { throw new BusinessException(400, "JSON 格式错误: " + e.getMessage()); } }
            case ENUM -> {
                if (opts == null || opts.isBlank()) return;
                for (String o : opts.split(",")) if (o.trim().equals(v.trim())) return;
                throw new BusinessException(400, "必须是 [" + opts + "] 之一: " + v);
            }
            case STRING -> {}
        }
    }

    public void evictCache() { cache.clear(); cacheLoadedAt = 0; }

    private void ensureLoaded() {
        long now = System.currentTimeMillis();
        if (!cache.isEmpty() && (now - cacheLoadedAt) < CACHE_TTL_MS) return;
        var all = repo.findAllOrderByGroupAndSort();
        cache.clear();
        for (var c : all) cache.put(c.getConfigKey(), c);
        cacheLoadedAt = now;
    }

    @Scheduled(fixedRate = 5 * 60_000) // 5 分钟兜底刷新
    public void scheduledRefresh() { evictCache(); ensureLoaded(); }
}
