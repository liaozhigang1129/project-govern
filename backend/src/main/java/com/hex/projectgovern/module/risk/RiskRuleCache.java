package com.hex.projectgovern.module.risk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


/**
 * 风险规则缓存 (V4.26) — 把 risk_bucket / risk_signal / risk_template 三张表全量加载到内存.
 *
 * <p><b>为什么需要 cache?</b>
 * <ul>
 *   <li>{@link com.hex.projectgovern.module.initiation.SowExtractor#extractRiskSignals} 在每次生成 AI WBS 时
 *       都要扫 106 个关键词 (旧硬编码 {@code Map.ofEntries}); 若每次查库, 慢且压力大.</li>
 *   <li>风险规则变更频率低 (天/周级别); 加载一次, 服务期内常驻内存.</li>
 *   <li>{@link #reload()} 可在 PMO_ADMIN 修改规则后主动热重载, 无需重启.</li>
 * </ul>
 *
 * <p><b>数据结构 (三层)</b>
 * <ol>
 *   <li>{@link #signalsByKeyword} — {@code Map<keyword, List<RiskSignal>>}, 按 keyword 索引, 加速 SOW contains 扫描</li>
 *   <li>{@link #templatesByBucket} — {@code Map<bucketCode, List<RiskTemplate>>}, 加速桶触发后批量取模板</li>
 *   <li>{@link #bucketsByCode} — {@code Map<code, RiskBucket>}, 校验桶是否启用</li>
 * </ol>
 *
 * <p><b>兼容性</b>: 重载后, 与旧硬编码 {@code Map.ofEntries} + {@code switch (bucket) addRisk(...)} 输出 100% 等价 (由 InitiationAiWbsDraftTest 验证).
 */
@Component
@Slf4j
public class RiskRuleCache {

    private final RiskBucketRepository bucketRepo;
    private final RiskSignalRepository signalRepo;
    private final RiskTemplateRepository templateRepo;
    private final ObjectMapper objectMapper;

    public RiskRuleCache(RiskBucketRepository bucketRepo,
                         RiskSignalRepository signalRepo,
                         RiskTemplateRepository templateRepo,
                         ObjectMapper objectMapper) {
        this.bucketRepo = bucketRepo;
        this.signalRepo = signalRepo;
        this.templateRepo = templateRepo;
        this.objectMapper = objectMapper;
    }

    /** 信号索引: keyword -> 命中此关键词的信号列表 (1 个 keyword 可对应多条信号, 例如 "数据标注" 与 "标注" 都打 DATA_LABEL) */
    @Getter
    private final AtomicReference<Map<String, List<RiskSignal>>> signalsByKeyword = new AtomicReference<>(Map.of());

    /** 模板索引: bucket_code -> 该桶下所有启用模板 */
    @Getter
    private final AtomicReference<Map<String, List<RiskTemplate>>> templatesByBucket = new AtomicReference<>(Map.of());

    /** 桶索引: code -> RiskBucket */
    @Getter
    private final AtomicReference<Map<String, RiskBucket>> bucketsByCode = new AtomicReference<>(Map.of());

    /** 加载时间 (供 /api/admin/risk/reload 健康检查) */
    @Getter
    private volatile Instant loadedAt = Instant.EPOCH;

    /** 当前加载条数 (调试用) */
    @Getter
    private volatile int lastSignalCount = 0;
    @Getter
    private volatile int lastTemplateCount = 0;
    @Getter
    private volatile int lastBucketCount = 0;

    /**
     * 启动时预加载 (ApplicationRunner 也行, @PostConstruct 简单稳).
     * <p>即使 DB 还未执行 V4.26 迁移, 也会以空 Map 启动; 等下次 reload 即可生效.
     */
    @PostConstruct
    public void init() {
        try {
            reload();
        } catch (Exception e) {
            log.warn("[RiskRuleCache] 启动加载失败 (可能 V4.26 尚未执行迁移): {}", e.getMessage());
            log.warn("[RiskRuleCache] 系统将以空规则启动, 请运维执行: POST /api/admin/risk/reload");
        }
    }

    /**
     * 全量重载: 三张表 → 内存索引. 事务保证一致性 (即使三表数据不一致, 也整体回滚).
     * <p>V4.31: 改用 {@code findAll*AndNotDeleted} 谓词, 已软删除的行不进 cache.
     */
    @Transactional(readOnly = true)
    public synchronized void reload() {
        List<RiskBucket> buckets = bucketRepo.findAllByEnabledTrueAndDeletedFalseOrderBySortOrderAsc();
        List<RiskSignal> signals = signalRepo.findAllEnabledAndNotDeleted();
        List<RiskTemplate> templates = templateRepo.findAllEnabledAndNotDeleted();

        Map<String, RiskBucket> bucketIdx = buckets.stream()
                .collect(Collectors.toMap(RiskBucket::getCode, b -> b, (a, b) -> a));

        Map<String, List<RiskSignal>> signalIdx = signals.stream()
                .collect(Collectors.groupingBy(RiskSignal::getKeyword));

        Map<String, List<RiskTemplate>> templateIdx = templates.stream()
                .collect(Collectors.groupingBy(RiskTemplate::getBucketCode));

        signalsByKeyword.set(Map.copyOf(signalIdx));
        templatesByBucket.set(Map.copyOf(templateIdx));
        bucketsByCode.set(Map.copyOf(bucketIdx));

        this.lastSignalCount = signals.size();
        this.lastTemplateCount = templates.size();
        this.lastBucketCount = buckets.size();
        this.loadedAt = Instant.now();

        log.info("[RiskRuleCache] reload 完成: buckets={}, signals={}, templates={}",
                buckets.size(), signals.size(), templates.size());
    }

    // ===== 供 SowExtractor / InitiationAiWbsService 调用 =====

    /**
     * 扫描 SOW 文本, 返回所有触发的桶 → 该桶下命中的 keyword 列表 (即原 ext.riskSignals()).
     * <p>原逻辑:
     * <pre>
     *   for (e : RISK_SIGNAL_TO_BUCKET.entrySet())
     *     if (text.contains(e.getKey()))
     *       out.computeIfAbsent(e.getValue(), ...).add(e.getKey());
     * </pre>
     * 行为完全一致: 信号无视行业 (industry=NULL 时), keyword contains 命中即触发.
     */
    public Map<String, List<String>> scanSignals(String sowText) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (sowText == null || sowText.isEmpty()) return out;
        for (var e : signalsByKeyword.get().entrySet()) {
            String keyword = e.getKey();
            if (sowText.contains(keyword)) {
                for (RiskSignal s : e.getValue()) {
                    // industry 限制 (当前数据全 NULL, 全部放行; 未来支持行业门控时启用)
                    if (s.getIndustry() != null && !s.getIndustry().isBlank()) {
                        // TODO: 传入 industry 参数后再启用 — 暂跳过
                        continue;
                    }
                    out.computeIfAbsent(s.getBucketCode(), k -> new ArrayList<>()).add(keyword);
                }
            }
        }
        return out;
    }

    /**
     * 取某桶下所有启用的模板 (用于 generateRisks 中按桶批量渲染).
     */
    public List<RiskTemplate> templatesOf(String bucketCode) {
        return templatesByBucket.get().getOrDefault(bucketCode, List.of());
    }

    /**
     * 校验桶是否启用. 用于 AI_MODEL / GENERIC 等"无信号但无条件触发"的桶.
     */
    public boolean isBucketEnabled(String bucketCode) {
        RiskBucket b = bucketsByCode.get().get(bucketCode);
        return b != null && Boolean.TRUE.equals(b.getEnabled());
    }

    /**
     * 取桶的默认 level / impact (供前端"新建模板"建议值).
     */
    public Optional<RiskBucket> bucketOf(String code) {
        return Optional.ofNullable(bucketsByCode.get().get(code));
    }

    /**
     * 解析 industry_in JSON 数组. 失败返回空集 (视为不限行业).
     */
    public Set<String> parseIndustryIn(String json) {
        if (json == null || json.isBlank()) return Set.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Set<String>>() {});
        } catch (Exception e) {
            log.warn("[RiskRuleCache] parseIndustryIn 失败: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * 解析 sow_contains_any JSON 数组. 失败返回空集 (视为无门控).
     */
    public Set<String> parseSowContainsAny(String json) {
        if (json == null || json.isBlank()) return Set.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Set<String>>() {});
        } catch (Exception e) {
            log.warn("[RiskRuleCache] parseSowContainsAny 失败: {}", e.getMessage());
            return Set.of();
        }
    }
}
