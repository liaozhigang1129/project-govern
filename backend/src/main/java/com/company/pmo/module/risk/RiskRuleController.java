package com.company.pmo.module.risk;

import com.company.pmo.common.api.ApiResponse;
import com.company.pmo.common.audit.AuditLog;
import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.common.security.RequireRoles;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * V4.26 风险规则管理 — CRUD 供 PMO 维护 (替换原硬编码 Map.ofEntries + switch).
 *
 * <p>3 类资源:
 * <ul>
 *   <li>/admin/risk/buckets     — 风险桶字典</li>
 *   <li>/admin/risk/signals     — SOW 关键词 → 桶 映射</li>
 *   <li>/admin/risk/templates   — 风险模板 (title/suggestion/level)</li>
 * </ul>
 *
 * <p>权限: 列表/详情 = {@link RequireRoles.Dict} (任意登录); 增删改 = {@link RequireRoles.Admin} (PMO_ADMIN/ADMIN).
 * <p>修改后必须 {@code POST /admin/risk/reload} 才生效 (或下次启动自动加载).
 */
@RestController
@RequestMapping("/admin/risk")
@RequiredArgsConstructor
@Tag(name = "Admin - Risk Rules", description = "风险规则管理 (V4.26 数据库化)")
public class RiskRuleController {

    private final RiskBucketRepository bucketRepo;
    private final RiskSignalRepository signalRepo;
    private final RiskTemplateRepository templateRepo;
    private final RiskRuleCache cache;

    // ===== Buckets =====

    @GetMapping("/buckets")
    @RequireRoles.Dict
    @Operation(summary = "风险桶列表 (含禁用, 已软删除的不展示)")
    public ApiResponse<List<RiskBucket>> listBuckets() {
        // V4.31: 列表只返回未软删除的桶, 避免前端编辑界面把"已删"的桶也展示出来
        return ApiResponse.ok(bucketRepo.findAll().stream()
                .filter(b -> !b.isDeleted())
                .toList());
    }

    @GetMapping("/buckets/{id}")
    @RequireRoles.Dict
    @Operation(summary = "风险桶详情")
    public ApiResponse<RiskBucket> getBucket(@PathVariable Long id) {
        return ApiResponse.ok(bucketRepo.findById(id)
                .orElseThrow(() -> new BusinessException("bucket not found: " + id)));
    }

    @PostMapping("/buckets")
    @RequireRoles.Admin
    @AuditLog(module = "RISK_RULE", action = "BUCKET_CREATE")
    @Operation(summary = "新建风险桶")
    public ApiResponse<RiskBucket> createBucket(@RequestBody RiskBucket body) {
        validateBucketCode(body.getCode());
        if (bucketRepo.existsByCode(body.getCode()))
            throw new BusinessException("bucket code exists: " + body.getCode());
        body.setId(null);
        return ApiResponse.ok(bucketRepo.save(body));
    }

    @PutMapping("/buckets/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "RISK_RULE", action = "BUCKET_UPDATE", extractResourceId = false)
    @Operation(summary = "更新风险桶")
    public ApiResponse<RiskBucket> updateBucket(@PathVariable Long id, @RequestBody RiskBucket body) {
        RiskBucket exist = bucketRepo.findById(id)
                .orElseThrow(() -> new BusinessException("bucket not found: " + id));
        // code 是自然主键, 不允许改
        if (!exist.getCode().equals(body.getCode()))
            throw new BusinessException("bucket code cannot be modified");
        exist.setName(body.getName());
        exist.setCategory(body.getCategory());
        exist.setDefaultLevel(body.getDefaultLevel());
        exist.setDefaultImpact(body.getDefaultImpact());
        exist.setSortOrder(body.getSortOrder());
        exist.setEnabled(body.getEnabled());
        exist.setRemark(body.getRemark());
        return ApiResponse.ok(bucketRepo.save(exist));
    }

    @DeleteMapping("/buckets/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "RISK_RULE", action = "BUCKET_DELETE")
    @Operation(summary = "软删除风险桶 (V4.31 改为软删, 不再物理删除)")
    public ApiResponse<Void> deleteBucket(@PathVariable Long id) {
        // V4.31: 改为软删除, 避免物理删除后级联 signal/template 外键失败
        RiskBucket exist = bucketRepo.findById(id)
                .orElseThrow(() -> new BusinessException("bucket not found: " + id));
        exist.setDeleted(true);
        bucketRepo.save(exist);
        return ApiResponse.ok();
    }

    // ===== Signals =====

    @GetMapping("/buckets/{code}/signals")
    @RequireRoles.Dict
    @Operation(summary = "某桶下的信号列表 (过滤软删除)")
    public ApiResponse<List<RiskSignal>> listSignalsByBucket(@PathVariable String code) {
        // V4.31: 编辑界面只展示未软删除的信号
        return ApiResponse.ok(signalRepo.findByBucketCodeAndDeletedFalseOrderByIdAsc(code));
    }

    @PostMapping("/signals")
    @RequireRoles.Admin
    @AuditLog(module = "RISK_RULE", action = "SIGNAL_CREATE")
    @Operation(summary = "新建风险信号")
    public ApiResponse<RiskSignal> createSignal(@RequestBody RiskSignal body) {
        if (body.getBucketCode() == null || body.getBucketCode().isBlank())
            throw new BusinessException("bucketCode required");
        if (body.getKeyword() == null || body.getKeyword().isBlank())
            throw new BusinessException("keyword required");
        if (!bucketRepo.existsByCode(body.getBucketCode()))
            throw new BusinessException("bucket not found: " + body.getBucketCode());
        if (signalRepo.existsByBucketCodeAndKeyword(body.getBucketCode(), body.getKeyword()))
            throw new BusinessException("signal exists: " + body.getBucketCode() + "/" + body.getKeyword());
        body.setId(null);
        if (body.getWeight() == null) body.setWeight(1);
        if (body.getEnabled() == null) body.setEnabled(true);
        return ApiResponse.ok(signalRepo.save(body));
    }

    @PutMapping("/signals/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "RISK_RULE", action = "SIGNAL_UPDATE", extractResourceId = false)
    @Operation(summary = "更新风险信号")
    public ApiResponse<RiskSignal> updateSignal(@PathVariable Long id, @RequestBody RiskSignal body) {
        RiskSignal exist = signalRepo.findById(id)
                .orElseThrow(() -> new BusinessException("signal not found: " + id));
        exist.setKeyword(body.getKeyword());
        exist.setIndustry(body.getIndustry());
        exist.setWeight(body.getWeight());
        exist.setEnabled(body.getEnabled());
        exist.setRemark(body.getRemark());
        return ApiResponse.ok(signalRepo.save(exist));
    }

    @DeleteMapping("/signals/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "RISK_RULE", action = "SIGNAL_DELETE")
    @Operation(summary = "软删除风险信号 (V4.31)")
    public ApiResponse<Void> deleteSignal(@PathVariable Long id) {
        // V4.31: 改为软删除
        RiskSignal exist = signalRepo.findById(id)
                .orElseThrow(() -> new BusinessException("signal not found: " + id));
        exist.setDeleted(true);
        signalRepo.save(exist);
        return ApiResponse.ok();
    }

    // ===== Templates =====

    @GetMapping("/buckets/{code}/templates")
    @RequireRoles.Dict
    @Operation(summary = "某桶下的模板列表 (过滤软删除)")
    public ApiResponse<List<RiskTemplate>> listTemplatesByBucket(@PathVariable String code) {
        // V4.31: 编辑界面只展示未软删除的模板
        return ApiResponse.ok(templateRepo.findByBucketCodeAndDeletedFalseOrderBySortOrderAscIdAsc(code));
    }

    @PostMapping("/templates")
    @RequireRoles.Admin
    @AuditLog(module = "RISK_RULE", action = "TEMPLATE_CREATE")
    @Operation(summary = "新建风险模板")
    public ApiResponse<RiskTemplate> createTemplate(@RequestBody RiskTemplate body) {
        validateTemplate(body);
        if (!bucketRepo.existsByCode(body.getBucketCode()))
            throw new BusinessException("bucket not found: " + body.getBucketCode());
        body.setId(null);
        return ApiResponse.ok(templateRepo.save(body));
    }

    @PutMapping("/templates/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "RISK_RULE", action = "TEMPLATE_UPDATE", extractResourceId = false)
    @Operation(summary = "更新风险模板")
    public ApiResponse<RiskTemplate> updateTemplate(@PathVariable Long id, @RequestBody RiskTemplate body) {
        RiskTemplate exist = templateRepo.findById(id)
                .orElseThrow(() -> new BusinessException("template not found: " + id));
        validateTemplate(body);
        exist.setBucketCode(body.getBucketCode());
        exist.setTitle(body.getTitle());
        exist.setSuggestion(body.getSuggestion());
        exist.setLevel(body.getLevel());
        exist.setProbability(body.getProbability());
        exist.setImpact(body.getImpact());
        exist.setAgentCode(body.getAgentCode());
        exist.setIndustryIn(body.getIndustryIn());
        exist.setSowContainsAny(body.getSowContainsAny());
        exist.setSortOrder(body.getSortOrder());
        exist.setEnabled(body.getEnabled());
        return ApiResponse.ok(templateRepo.save(exist));
    }

    @DeleteMapping("/templates/{id}")
    @RequireRoles.Admin
    @AuditLog(module = "RISK_RULE", action = "TEMPLATE_DELETE")
    @Operation(summary = "软删除风险模板 (V4.31)")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        // V4.31: 改为软删除
        RiskTemplate exist = templateRepo.findById(id)
                .orElseThrow(() -> new BusinessException("template not found: " + id));
        exist.setDeleted(true);
        templateRepo.save(exist);
        return ApiResponse.ok();
    }

    // ===== Cache 管理 =====

    @PostMapping("/reload")
    @RequireRoles.Admin
    @AuditLog(module = "RISK_RULE", action = "RELOAD")
    @Operation(summary = "热重载: 从 DB 全量刷新到内存 cache")
    public ApiResponse<Map<String, Object>> reload() {
        cache.reload();
        return ApiResponse.ok(Map.of(
                "buckets", cache.getLastBucketCount(),
                "signals", cache.getLastSignalCount(),
                "templates", cache.getLastTemplateCount(),
                "loadedAt", cache.getLoadedAt()
        ));
    }

    @GetMapping("/cache-status")
    @RequireRoles.Dict
    @Operation(summary = "查看当前 cache 状态")
    public ApiResponse<Map<String, Object>> cacheStatus() {
        return ApiResponse.ok(Map.of(
                "buckets", cache.getLastBucketCount(),
                "signals", cache.getLastSignalCount(),
                "templates", cache.getLastTemplateCount(),
                "loadedAt", cache.getLoadedAt()
        ));
    }

    // ===== Validation =====

    private void validateBucketCode(String code) {
        if (code == null || code.isBlank())
            throw new BusinessException("code required");
        if (!code.matches("^[A-Z][A-Z0-9_]{1,49}$"))
            throw new BusinessException("code must be UPPER_SNAKE_CASE (e.g. DATA_LABEL), got: " + code);
    }

    private void validateTemplate(RiskTemplate t) {
        if (t.getBucketCode() == null || t.getBucketCode().isBlank())
            throw new BusinessException("bucketCode required");
        if (t.getTitle() == null || t.getTitle().isBlank())
            throw new BusinessException("title required");
        if (t.getProbability() == null || t.getProbability() < 1 || t.getProbability() > 5)
            throw new BusinessException("probability must be 1-5");
        if (t.getImpact() == null || t.getImpact() < 1 || t.getImpact() > 5)
            throw new BusinessException("impact must be 1-5");
        // JSON 字段合法性
        if (t.getIndustryIn() != null && !t.getIndustryIn().isBlank()) {
            try {
                new ObjectMapper().readTree(t.getIndustryIn());
            } catch (Exception e) {
                throw new BusinessException("industryIn must be JSON array, got: " + t.getIndustryIn());
            }
        }
        if (t.getSowContainsAny() != null && !t.getSowContainsAny().isBlank()) {
            try {
                new ObjectMapper().readTree(t.getSowContainsAny());
            } catch (Exception e) {
                throw new BusinessException("sowContainsAny must be JSON array, got: " + t.getSowContainsAny());
            }
        }
    }
}
