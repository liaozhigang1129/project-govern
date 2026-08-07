package com.hex.projectgovern.module.initiation;

import com.hex.projectgovern.common.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step 6 — 立项预算快照 + 毛利计算服务。
 * <p>业务规则:
 * <ol>
 *   <li>contractAmount 来自 project_initiation.contract_amount(可被请求覆盖)</li>
 *   <li>resourceCost = sum(initiation_resource_plan.cost_amount, deleted=false)</li>
 *   <li>riskCost     = sum(initiation_risk_response.response_cost, deleted=false)</li>
 *   <li>otherCost    = 来自请求(差旅/采购/...)或 0</li>
 *   <li>totalCost    = resource + risk + other</li>
 *   <li>margin       = contractAmount - totalCost</li>
 *   <li>marginPct    = (margin / contractAmount) × 100, contractAmount=0 时返回 0</li>
 *   <li>快照写入 initiation_budget_freeze,UK 唯一约束保证 1 个 active</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitiationBudgetFreezeService {

    private final InitiationBudgetFreezeRepository repo;
    private final InitiationResourcePlanService resourceService;
    private final InitiationRiskResponseService riskService;
    private final ProjectInitiationRepository initiationRepo;
    private final ObjectMapper objectMapper;

    public record FreezeRequest(BigDecimal otherCost, BigDecimal contractAmountOverride) {}

    @Transactional
    public InitiationBudgetFreeze freeze(Long initiationId, Long actorId, FreezeRequest req) {
        ProjectInitiation init = initiationRepo.findById(initiationId)
                .filter(i -> !i.isDeleted())
                .orElseThrow(() -> new BusinessException(404, "Initiation not found: " + initiationId));

        BigDecimal contract = req != null && req.contractAmountOverride() != null
                ? req.contractAmountOverride()
                : (init.getContractAmount() == null ? BigDecimal.ZERO : init.getContractAmount());
        BigDecimal resource = resourceService.totalCost(initiationId);
        BigDecimal risk     = riskService.totalCost(initiationId);
        BigDecimal other    = (req != null && req.otherCost() != null) ? req.otherCost() : BigDecimal.ZERO;
        BigDecimal total    = resource.add(risk).add(other);
        BigDecimal margin   = contract.subtract(total);
        BigDecimal marginPct = contract.signum() == 0
                ? BigDecimal.ZERO
                : margin.multiply(BigDecimal.valueOf(100)).divide(contract, 2, RoundingMode.HALF_UP);

        // 详情快照
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("initiationId", initiationId);
        snap.put("initiationCode", init.getCode());
        snap.put("contractAmount", contract);
        snap.put("resourceCost", resource);
        snap.put("riskCost", risk);
        snap.put("otherCost", other);
        snap.put("totalCost", total);
        snap.put("margin", margin);
        snap.put("marginPct", marginPct);
        snap.put("frozenBy", actorId);
        snap.put("frozenAt", Instant.now().toString());
        String json;
        try { json = objectMapper.writeValueAsString(snap); }
        catch (Exception e) { throw new BusinessException(500, "Failed to serialize snapshot: " + e.getMessage()); }

        // 删除旧 active(若存在),再写新
        repo.findByInitiationIdAndDeletedFalse(initiationId).ifPresent(old -> {
            old.setDeleted(true);
            repo.save(old);
        });

        InitiationBudgetFreeze f = new InitiationBudgetFreeze();
        f.setInitiationId(initiationId);
        f.setFrozenBy(actorId);
        f.setFrozenAt(Instant.now());
        f.setContractAmount(contract);
        f.setResourceCost(resource);
        f.setRiskCost(risk);
        f.setOtherCost(other);
        f.setTotalCost(total);
        f.setMargin(margin);
        f.setMarginPct(marginPct);
        f.setSnapshotJson(json);
        return repo.save(f);
    }

    @Transactional(readOnly = true)
    public InitiationBudgetFreeze latest(Long initiationId) {
        return repo.findByInitiationIdAndDeletedFalse(initiationId).orElse(null);
    }

    public Map<String, Object> parseSnapshot(InitiationBudgetFreeze f) {
        if (f == null || f.getSnapshotJson() == null) return Map.of();
        try {
            return objectMapper.readValue(f.getSnapshotJson(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse freeze snapshot: {}", e.getMessage());
            return Map.of();
        }
    }
}
