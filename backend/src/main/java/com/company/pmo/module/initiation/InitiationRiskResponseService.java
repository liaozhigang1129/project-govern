package com.company.pmo.module.initiation;

import com.company.pmo.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Step 5 — 立项阶段风险应对服务。
 * <p>独立于项目级 risk 模块,允许"先有立项风险"再演化为项目风险。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitiationRiskResponseService {

    private final InitiationRiskResponseRepository repo;

    private static final List<String> ALLOWED_LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final List<String> ALLOWED_STATUS = List.of("PLANNED", "IN_PROGRESS", "DONE", "CANCELLED");

    @Transactional
    public InitiationRiskResponse save(InitiationRiskResponse r) {
        if (r.getRiskTitle() == null || r.getRiskTitle().isBlank()) {
            throw new BusinessException(400, "riskTitle is required");
        }
        if (r.getRiskLevel() != null && !ALLOWED_LEVELS.contains(r.getRiskLevel())) {
            throw new BusinessException(400, "Invalid riskLevel: " + r.getRiskLevel());
        }
        if (r.getStatus() != null && !ALLOWED_STATUS.contains(r.getStatus())) {
            throw new BusinessException(400, "Invalid status: " + r.getStatus());
        }
        if (r.getResponseCost() == null) r.setResponseCost(BigDecimal.ZERO);
        if (r.getRiskLevel() == null) r.setRiskLevel("MEDIUM");
        if (r.getStatus() == null) r.setStatus("PLANNED");
        return repo.save(r);
    }

    @Transactional(readOnly = true)
    public List<InitiationRiskResponse> list(Long initiationId) {
        return repo.findByInitiationIdAndDeletedFalseOrderByIdAsc(initiationId);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalCost(Long initiationId) {
        return list(initiationId).stream()
                .map(InitiationRiskResponse::getResponseCost)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void softDelete(Long id) {
        repo.findById(id).ifPresent(r -> {
            r.setDeleted(true);
            repo.save(r);
        });
    }

    /** V4.19: 全量软删(前端 Step 5 "先清空再批量存" 用) */
    @Transactional
    public int deleteAllByInitiation(Long initiationId) {
        List<InitiationRiskResponse> all = repo.findByInitiationIdAndDeletedFalseOrderByIdAsc(initiationId);
        for (InitiationRiskResponse r : all) {
            r.setDeleted(true);
            repo.save(r);
        }
        return all.size();
    }
}
