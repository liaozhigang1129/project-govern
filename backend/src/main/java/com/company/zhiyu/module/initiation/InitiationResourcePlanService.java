package com.company.zhiyu.module.initiation;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.module.cost.HourlyRate;
import com.company.zhiyu.module.cost.HourlyRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Step 4 — 人力资源派遣计划服务。
 * <p>关键逻辑:
 * <ol>
 *   <li>按 role_code 取最近一份 HourlyRate(role 不变费率最新行)</li>
 *   <li>costAmount = planHours × rate × (allocationPct/100)</li>
 *   <li>汇总返回总成本(供 Step 6 用)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitiationResourcePlanService {

    private final InitiationResourcePlanRepository repo;
    private final HourlyRateRepository rateRepo;

    @Transactional
    public InitiationResourcePlan save(InitiationResourcePlan p) {
        if (p.getUserId() == null && (p.getRoleCode() == null || p.getRoleCode().isBlank())) {
            throw new BusinessException(400, "userId or roleCode must be provided");
        }
        if (p.getAllocationPct() == null || p.getAllocationPct() < 0 || p.getAllocationPct() > 100) {
            throw new BusinessException(400, "allocationPct must be 0-100: " + p.getAllocationPct());
        }
        // 锁定费率(优先用请求里的 hourlyRate,否则按 roleCode 查最新)
        if (p.getHourlyRate() == null || p.getHourlyRate().signum() == 0) {
            if (p.getRoleCode() != null && !p.getRoleCode().isBlank()) {
                Optional<HourlyRate> latest = rateRepo.findAllByOrderByEffectiveMonthDescIdDesc().stream()
                        .filter(r -> p.getRoleCode().equalsIgnoreCase(r.getRoleCode()))
                        .findFirst();
                latest.ifPresent(r -> p.setHourlyRate(r.getRate()));
            }
        }
        // 算成本
        BigDecimal hours = p.getPlanHours() == null ? BigDecimal.ZERO : p.getPlanHours();
        BigDecimal rate  = p.getHourlyRate() == null ? BigDecimal.ZERO : p.getHourlyRate();
        BigDecimal pct   = BigDecimal.valueOf(p.getAllocationPct() == null ? 100 : p.getAllocationPct());
        BigDecimal cost  = hours.multiply(rate).multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        p.setCostAmount(cost);
        if (p.getId() == null) {
            // 新建
        } else {
            // 更新前软删旧的(简化:直接 save 即可,JPA 主键匹配)
        }
        return repo.save(p);
    }

    @Transactional(readOnly = true)
    public List<InitiationResourcePlan> list(Long initiationId) {
        return repo.findByInitiationIdAndDeletedFalseOrderByIdAsc(initiationId);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalCost(Long initiationId) {
        return list(initiationId).stream()
                .map(InitiationResourcePlan::getCostAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void softDelete(Long id) {
        repo.findById(id).ifPresent(p -> {
            p.setDeleted(true);
            repo.save(p);
        });
    }

    /** V4.19: 全量软删(前端 Step 4 "先清空再批量存" 用) */
    @Transactional
    public int deleteAllByInitiation(Long initiationId) {
        List<InitiationResourcePlan> all = repo.findByInitiationIdAndDeletedFalseOrderByIdAsc(initiationId);
        for (InitiationResourcePlan p : all) {
            p.setDeleted(true);
            repo.save(p);
        }
        return all.size();
    }
}
