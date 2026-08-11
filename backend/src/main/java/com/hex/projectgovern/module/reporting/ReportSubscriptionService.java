package com.hex.projectgovern.module.reporting;

import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.module.reporting.dto.ReportingDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportSubscriptionService {
    private final ReportSubscriptionRepository repo;
    private final ReportTemplateRepository templateRepo;

    @Transactional
    public ReportSubscription create(SubscriptionRequest req) {
        if (repo.findByCode(req.code()).isPresent()) {
            // 重名 → 抛错
        }
        if (req.templateId() != null && !templateRepo.existsById(req.templateId())) {
            throw new BusinessException("Template not found: " + req.templateId());
        }
        ReportSubscription s = new ReportSubscription();
        s.setCode(req.code());
        s.setUserId(req.userId() == null ? 0L : req.userId());
        s.setTemplateId(req.templateId());
        s.setDashboardId(req.dashboardId());
        s.setChannelSet(req.channelSet());
        s.setCron(req.cron());
        s.setRecipients(req.recipients());
        s.setParams(req.params());
        s.setStatus("ACTIVE");
        s.setNextRunAt(Instant.now());  // 第一次立即跑
        return repo.save(s);
    }

    @Transactional(readOnly = true)
    public List<ReportSubscription> listByUser(Long userId) {
        return repo.findByUserId(userId);
    }

    @Transactional
    public ReportSubscription pause(Long id) {
        ReportSubscription s = repo.findById(id).orElseThrow(() -> new BusinessException("Subscription not found"));
        s.setStatus("PAUSED");
        s.setNextRunAt(null);
        return s;
    }

    @Transactional
    public ReportSubscription resume(Long id) {
        ReportSubscription s = repo.findById(id).orElseThrow(() -> new BusinessException("Subscription not found"));
        s.setStatus("ACTIVE");
        s.setNextRunAt(Instant.now());
        return s;
    }

    /** 立即触发一次 (用于手动 run) */
    @Transactional
    public ReportSubscription triggerNow(Long id) {
        ReportSubscription s = repo.findById(id).orElseThrow(() -> new BusinessException("Subscription not found"));
        s.setLastRunAt(Instant.now());
        s.setNextRunAt(Instant.now().plusSeconds(86400));  // 24h 后再跑
        return s;
    }

    @Transactional(readOnly = true)
    public List<ReportSubscription> findDue() {
        return repo.findDueSubscriptions(Instant.now());
    }
}
