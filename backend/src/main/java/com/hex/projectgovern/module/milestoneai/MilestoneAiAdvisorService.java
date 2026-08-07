package com.hex.projectgovern.module.milestoneai;

import com.hex.projectgovern.common.exception.BusinessException;
import com.hex.projectgovern.module.milestone.Milestone;
import com.hex.projectgovern.module.milestone.MilestoneAnalysisService;
import com.hex.projectgovern.module.milestone.MilestonePhase;
import com.hex.projectgovern.module.milestone.MilestoneRepository;
import com.hex.projectgovern.module.project.Project;
import com.hex.projectgovern.module.project.ProjectRepository;
import com.hex.projectgovern.module.risk.dto.RiskRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.hex.projectgovern.module.risk.RiskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * P5-里程碑 AI 预警服务
 *
 * 职责:
 *  - runForMilestone: 跑规则引擎 (5 维信号 + 评分)
 *  - runBatch: 批跑 (复用 MilestoneAnalysisService 权限)
 *  - apply: 建议 → 落地为风险
 *  - reject: 标 REJECTED, 写理由
 *  - getById / listByProject / summary: 查询
 *
 * IM 推送: runForMilestone 完成后, CRITICAL/WARNING 自动发
 *  com.hex.projectgovern.module.notification.MilestoneAdvisoryDecidedEvent → NotificationDispatcherListener 异步消费
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MilestoneAiAdvisorService {

    private final MilestoneAiAdvisor advisor;
    private final MilestoneAiAdvisoryRepository advisoryRepo;
    private final MilestoneAiSignalRepository signalRepo;
    private final MilestoneRepository milestoneRepo;
    private final ProjectRepository projectRepo;
    private final MilestoneAnalysisService analysisService;
    private final ObjectMapper mapper;
    @Autowired @Lazy private RiskService riskService;
    @Autowired private ApplicationEventPublisher eventPublisher;

    /**
     * 跑单个里程碑的 AI 规则引擎。
     *
     * 输入: projectId + milestoneId + 5 维信号原始数据 (SPI/phaseLag/velocity/historical)
     * 过程: 调 advisor.analyze() → AdviceResult → 落库 + 落 5 维信号明细 + publish IM 事件
     * 输出: AdvisoryDto
     */
    @Transactional
    public MilestoneAiAdvisoryDto runForMilestone(
            Long projectId, Long milestoneId, Long operatorId,
            Double spi, Integer phaseLagDays, Double velocityDeltaPct, Double historicalHitRate) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new BusinessException(404, "项目不存在"));
        Milestone m = milestoneRepo.findById(milestoneId)
                .orElseThrow(() -> new BusinessException(404, "里程碑不存在"));
        MilestonePhase phase = m.getPhase();

        // 默认值兜底
        if (spi == null) spi = 1.0;
        if (phaseLagDays == null) phaseLagDays = 0;
        if (velocityDeltaPct == null) velocityDeltaPct = 0.0;
        if (historicalHitRate == null) historicalHitRate = 0.5;

        // 1. 调 advisor 纯函数
        MilestoneAiAdvisor.AdviceResult r = advisor.analyze(
                project, m, phase, spi, phaseLagDays, velocityDeltaPct, historicalHitRate);

        // 2. 落主表
        MilestoneAiAdvisory a = new MilestoneAiAdvisory();
        a.setProjectId(projectId);
        a.setMilestoneId(milestoneId);
        a.setPhaseId(phase == null ? null : phase.getId());
        a.setPhaseCode(phase == null ? null : phase.getCode());
        a.setPhaseName(phase == null ? null : phase.getName());
        a.setMilestoneName(m.getName());
        a.setMilestonePlanDate(m.getPlanDate());
        a.setMilestoneStatusCode(m.getStatus() == null ? null : m.getStatus().getCode());
        a.setSeverity(r.severity());
        a.setScore(r.score());
        a.setConfidence(r.confidence());
        a.setSignalOverdue(r.signals().get(0).intensity());
        a.setSignalSpi(r.signals().get(1).intensity());
        a.setSignalPhaseLag(r.signals().get(2).intensity());
        a.setSignalVelocity(r.signals().get(3).intensity());
        a.setSignalHistorical(r.signals().get(4).intensity());
        a.setReasonsJson(serialize(r.reasons()));
        a.setSuggestionsJson(serialize(r.suggestions()));
        a.setCategory(r.category());
        a.setSuggestedProbability(r.suggestedProbability());
        a.setSuggestedImpact(r.suggestedImpact());
        a.setStatus("PENDING");
        a.setModelVersion("rule-engine-v1.0");
        a.setDecidedAt(Instant.now());
        a.setFingerprint(r.fingerprint());
        MilestoneAiAdvisory saved = advisoryRepo.save(a);

        // 3. 落 5 维信号明细
        for (MilestoneAiAdvisor.Signal s : r.signals()) {
            MilestoneAiSignal sig = new MilestoneAiSignal();
            sig.setAdvisoryId(saved.getId());
            sig.setSignalType(s.type());
            sig.setIntensity(s.intensity());
            sig.setWeight(s.weight());
            sig.setScore(s.score());
            sig.setDescription(s.description());
            sig.setMissing(s.missing());
            signalRepo.save(sig);
        }

        // 4. P5-IM 推送 (CRITICAL/WARNING 自动发事件)
        publishAdvisoryEvent(saved, project, m);
        return toDto(saved, signalRepo.findByAdvisoryIdOrderByIdAsc(saved.getId()));
    }

    public MilestoneAiAdvisoryDto getById(Long id) {
        MilestoneAiAdvisory a = advisoryRepo.findById(id)
                .orElseThrow(() -> new BusinessException(404, "建议不存在"));
        return toDto(a, signalRepo.findByAdvisoryIdOrderByIdAsc(id));
    }

    /** 取 advisory 实体 (含 5 维信号, 给 ML 预测器用) */
    public java.util.Optional<MilestoneAiAdvisory> getEntityById(Long id) {
        return advisoryRepo.findById(id);
    }

    public List<MilestoneAiAdvisoryDto> listByProject(Long projectId, String status, String severity) {
        List<MilestoneAiAdvisory> list;
        // 简化版: 不带 status/severity 过滤, 全部按 id 倒序 (Repository 已有方法)
        list = advisoryRepo.findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(projectId, PageRequest.of(0, 500)).getContent();
        // 业务层过滤
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            final String s = status.toUpperCase();
            list = list.stream().filter(a -> s.equals(a.getStatus())).toList();
        }
        if (severity != null && !severity.isBlank() && !"ALL".equalsIgnoreCase(severity)) {
            final String s = severity.toUpperCase();
            list = list.stream().filter(a -> s.equals(a.getSeverity())).toList();
        }
        return list.stream().map(a -> toDto(a, signalRepo.findByAdvisoryIdOrderByIdAsc(a.getId()))).toList();
    }

    public Map<String, Object> summary(Long projectId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("projectId", projectId);
        List<MilestoneAiAdvisory> all = advisoryRepo.findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(projectId, PageRequest.of(0, 1000)).getContent();
        int critical = 0, warning = 0, info = 0, pending = 0, applied = 0, rejected = 0;
        BigDecimal sumScore = BigDecimal.ZERO;
        Instant lastRun = null;
        for (MilestoneAiAdvisory a : all) {
            String sev = a.getSeverity() == null ? "INFO" : a.getSeverity();
            if ("CRITICAL".equals(sev)) critical++;
            else if ("WARNING".equals(sev)) warning++;
            else info++;
            if ("PENDING".equals(a.getStatus())) pending++;
            else if ("APPLIED".equals(a.getStatus())) applied++;
            else if ("REJECTED".equals(a.getStatus())) rejected++;
            if (a.getScore() != null) sumScore = sumScore.add(a.getScore());
            if (lastRun == null || (a.getDecidedAt() != null && a.getDecidedAt().isAfter(lastRun))) {
                lastRun = a.getDecidedAt();
            }
        }
        r.put("total", all.size());
        r.put("critical", critical);
        r.put("warning", warning);
        r.put("info", info);
        r.put("pending", pending);
        r.put("applied", applied);
        r.put("rejected", rejected);
        r.put("avgScore", all.isEmpty() ? BigDecimal.ZERO : sumScore.divide(BigDecimal.valueOf(all.size()), 2, java.math.RoundingMode.HALF_UP));
        r.put("lastRunAt", lastRun == null ? null : lastRun.toString());
        return r;
    }

    @Transactional
    public MilestoneAiAdvisoryDto apply(Long advisoryId, Long operatorId) {
        MilestoneAiAdvisory a = advisoryRepo.findById(advisoryId)
                .orElseThrow(() -> new BusinessException(404, "建议不存在"));
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessException(409, "该建议状态为 " + a.getStatus() + ", 不可重复落地");
        }
        // 调 RiskService.save() 复用风险模块
        // RiskRequest 是 record (17 字段), 必须用全参数构造器
        // owner: 项目 PM (从 Project 取)
        Project p = projectRepo.findById(a.getProjectId()).orElse(null);
        Long pmUserId = p == null ? null : p.getPmUserId();
        RiskRequest req = new RiskRequest(
                null,                                                  // id
                a.getProjectId(),                                      // projectId
                "R-AI-" + a.getId(),                                 // code
                "AI预警: " + a.getMilestoneName(),                    // title
                buildRiskDescription(a),                               // description
                a.getCategory() == null ? "SCHEDULE" : a.getCategory(),// category
                a.getSuggestedProbability(),                           // probability 1-5
                a.getSuggestedImpact(),                                // impact 1-5
                "OPEN",                                               // status
                pmUserId,                                              // ownerUserId
                "请参考 AI 建议 5 维信号逐项落实",                  // mitigation
                null,                                                  // contingency
                "MITIGATE",                                           // responseStrategy
                java.time.LocalDate.now(),                             // identifiedDate
                null,                                                  // targetCloseDate
                null,                                                  // relatedWbsTaskId
                a.getMilestoneId()                                     // relatedMilestoneId
        );
        com.hex.projectgovern.module.risk.dto.RiskResponse risk = riskService.save(req, operatorId);
        a.setStatus("APPLIED");
        a.setAppliedAt(Instant.now());
        a.setAppliedBy(operatorId);
        a.setAppliedRiskId(risk.id());
        advisoryRepo.save(a);
        return toDto(a, signalRepo.findByAdvisoryIdOrderByIdAsc(a.getId()));
    }

    @Transactional
    public MilestoneAiAdvisoryDto reject(Long advisoryId, Long operatorId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException(400, "拒绝理由不能为空");
        }
        MilestoneAiAdvisory a = advisoryRepo.findById(advisoryId)
                .orElseThrow(() -> new BusinessException(404, "建议不存在"));
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessException(409, "该建议状态为 " + a.getStatus() + ", 不可拒绝");
        }
        a.setStatus("REJECTED");
        a.setRejectReason(reason.trim());
        a.setRejectedBy(operatorId);
        a.setRejectedAt(Instant.now());
        advisoryRepo.save(a);
        return toDto(a, signalRepo.findByAdvisoryIdOrderByIdAsc(a.getId()));
    }

    @Transactional
    public int runBatch(String scope, Long buId, Long plId, Long viewerUserId) {
        long start = System.currentTimeMillis();
        List<Long> projectIds = new ArrayList<>(analysisService.resolveProjectIds(scope, buId, plId, viewerUserId));
        if (projectIds.isEmpty()) return 0;
        int scanned = 0, newAdvisories = 0;
        for (Long pid : projectIds) {
            List<Milestone> miles = milestoneRepo.findByProjectIdAndDeletedFalseOrderBySequence(pid);
            for (Milestone m : miles) {
                if (m.getStatus() == null) continue;
                if ("DONE".equals(m.getStatus().getCode()) || "CANCELLED".equals(m.getStatus().getCode())) continue;
                scanned++;
                try {
                    int before = advisoryRepo.findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(pid, PageRequest.of(0, 200)).getNumberOfElements();
                    // 默认信号值: SPI=1.0, lag=0, velocity=0, historical=0.5 (实际项目可计算)
                    runForMilestone(pid, m.getId(), viewerUserId, 1.0, 0, 0.0, 0.5);
                    int after = advisoryRepo.findByProjectIdAndDeletedFalseOrderByScoreDescIdAsc(pid, PageRequest.of(0, 200)).getNumberOfElements();
                    if (after > before) newAdvisories++;
                } catch (Exception ex) {
                    log.warn("[AI Batch] 单条失败: projectId={} milestoneId={} err={}",
                            pid, m.getId(), ex.getMessage());
                }
            }
        }
        long dur = System.currentTimeMillis() - start;
        log.info("[AI Batch] 扫 {} 新建 {} 耗时 {}ms", scanned, newAdvisories, dur);
        return newAdvisories;
    }

    // ============================================================
    // 内部工具
    // ============================================================
    private String serialize(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { return "[]"; }
    }

    private MilestoneAiAdvisoryDto toDto(MilestoneAiAdvisory a, List<MilestoneAiSignal> sigs) {
        return new MilestoneAiAdvisoryDto(
                a.getId(),                          // id
                a.getProjectId(),                   // projectId
                a.getMilestoneId(),                 // milestoneId
                a.getPhaseId(),                     // phaseId
                a.getPhaseCode(),                   // phaseCode
                a.getPhaseName(),                   // phaseName
                a.getMilestoneName(),               // milestoneName
                a.getMilestonePlanDate(),           // milestonePlanDate
                a.getMilestoneStatusCode(),         // milestoneStatusCode
                a.getSeverity() == null ? "INFO" : a.getSeverity(), // severity
                a.getScore() == null ? BigDecimal.ZERO : a.getScore(), // score
                a.getConfidence() == null ? BigDecimal.ZERO : a.getConfidence(), // confidence
                a.getSignalOverdue() == null ? BigDecimal.ZERO : a.getSignalOverdue(), // signalOverdue
                a.getSignalSpi() == null ? BigDecimal.ZERO : a.getSignalSpi(), // signalSpi
                a.getSignalPhaseLag() == null ? BigDecimal.ZERO : a.getSignalPhaseLag(), // signalPhaseLag
                a.getSignalVelocity() == null ? BigDecimal.ZERO : a.getSignalVelocity(), // signalVelocity
                a.getSignalHistorical() == null ? BigDecimal.ZERO : a.getSignalHistorical(), // signalHistorical
                parseJsonNode(a.getReasonsJson()),   // reasons (JsonNode)
                parseJsonNode(a.getSuggestionsJson()), // suggestions (JsonNode)
                a.getCategory() == null ? "SCHEDULE" : a.getCategory(), // category
                a.getSuggestedProbability() == null ? 3 : a.getSuggestedProbability(), // suggestedProbability
                a.getSuggestedImpact() == null ? 3 : a.getSuggestedImpact(), // suggestedImpact
                a.getStatus() == null ? "PENDING" : a.getStatus(), // status
                a.getModelVersion(),                 // modelVersion
                a.getDecidedAt(),                    // decidedAt (Instant)
                a.getAppliedAt(),                    // appliedAt
                a.getAppliedBy(),                    // appliedBy
                a.getAppliedRiskId(),                // appliedRiskId
                a.getMlPredictedAt(),                // mlPredictedAt (P5)
                a.getMlSeverity(),                  // mlSeverity (P5)
                a.getMlConfidence(),                // mlConfidence (P5)
                a.getLlmSummary(),
                a.getFeedbackType(),
                a.getFeedbackAt(),
                a.getFeedbackNote(),                  // llmSummary (P5)
                a.getRejectedAt(),                   // rejectedAt
                a.getRejectedBy(),                   // rejectedBy
                a.getRejectReason(),                 // rejectReason
                a.getFingerprint(),                  // fingerprint (String)
                a.getCreatedAt(),                    // createdAt
                a.getUpdatedAt(),                    // updatedAt
                toSignalDtos(sigs)                   // signals
        );
    }

    private List<com.hex.projectgovern.module.milestoneai.MilestoneAiSignalDto> toSignalDtos(List<MilestoneAiSignal> sigs) {
        if (sigs == null) return List.of();
        List<com.hex.projectgovern.module.milestoneai.MilestoneAiSignalDto> r = new java.util.ArrayList<>();
        for (MilestoneAiSignal s : sigs) r.add(toSignalDto(s));
        return r;
    }

    private com.hex.projectgovern.module.milestoneai.MilestoneAiSignalDto toSignalDto(MilestoneAiSignal s) {
        return new com.hex.projectgovern.module.milestoneai.MilestoneAiSignalDto(
                s.getId(),
                s.getSignalType() == null ? "OVERDUE" : s.getSignalType(),
                s.getIntensity() == null ? BigDecimal.ZERO : s.getIntensity(),
                s.getWeight() == null ? BigDecimal.ZERO : s.getWeight(),
                s.getScore() == null ? BigDecimal.ZERO : s.getScore(),
                s.getDescription(),
                s.isMissing()
        );
    }

    private com.fasterxml.jackson.databind.JsonNode parseJsonNode(String json) {
        if (json == null || json.isBlank()) return mapper.createArrayNode();
        try { return mapper.readTree(json); }
        catch (Exception e) { return mapper.createArrayNode(); }
    }
    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) { return List.of(); }
    }

    private String buildRiskDescription(MilestoneAiAdvisory a) {
        StringBuilder sb = new StringBuilder();
        sb.append("【AI 规则引擎 v1.0 自动生成】\n");
        sb.append("里程碑: ").append(a.getMilestoneName()).append("\n");
        sb.append("评分: ").append(a.getScore()).append(" / 置信度: ")
                .append(a.getConfidence()).append("\n");
        List<String> reasons = parseStringList(a.getReasonsJson());
        if (!reasons.isEmpty()) {
            sb.append("原因: ").append(String.join("; ", reasons)).append("\n");
        }
        sb.append("建议概率/影响: ").append(a.getSuggestedProbability()).append("/").append(a.getSuggestedImpact());
        return sb.toString();
    }

    /**
     * P5-IM 推送: 异步发事件, NotificationDispatcherListener 收 → 写 UNREAD + IM 通道
     */
    private void publishAdvisoryEvent(MilestoneAiAdvisory advisory, Project p, Milestone m) {
        try {
            // 收件人: PM + Sponsor + operator
            List<Long> recipients = new ArrayList<>();
            if (p != null && p.getPmUserId() != null) recipients.add(p.getPmUserId());
            if (p != null && p.getSponsorUserId() != null) recipients.add(p.getSponsorUserId());
            if (p == null ? null : p.getPmUserId() != null && !recipients.contains(p == null ? null : p.getPmUserId())) {
                if (p != null) { Long pm = p.getPmUserId(); if (pm != null && !recipients.contains(pm)) recipients.add(pm); }
            }
            if (recipients.isEmpty()) return;
            // INFO 级别默认不推送 (避免噪音)
            if ("INFO".equalsIgnoreCase(advisory.getSeverity())) {
                log.debug("[AI Advisor] INFO 级别默认不推送: advisoryId={}", advisory.getId());
                return;
            }
            String sevEmoji = "CRITICAL".equals(advisory.getSeverity()) ? "🔴"
                    : "WARNING".equals(advisory.getSeverity()) ? "🟡" : "🟢";
            String title = sevEmoji + " 里程碑 AI 预警: " + advisory.getMilestoneName();
            String summary = String.format("项目【%s】里程碑【%s】得分 %s · 置信 %s%%",
                    p == null ? "?" : p.getName(),
                    advisory.getMilestoneName(),
                    advisory.getScore(),
                    advisory.getConfidence());

            String code = "AI-M-" + advisory.getId();

            com.hex.projectgovern.module.notification.MilestoneAdvisoryDecidedEvent event = new com.hex.projectgovern.module.notification.MilestoneAdvisoryDecidedEvent(
                    advisory.getId(), code, title, summary,
                    advisory.getProjectId(),
                    p == null ? null : p.getName(),
                    advisory.getMilestoneId(),
                    advisory.getMilestoneName(),
                    advisory.getSeverity() == null ? "INFO" : advisory.getSeverity(),
                    advisory.getScore() == null ? 0.0 : advisory.getScore().doubleValue(),
                    advisory.getConfidence() == null ? 0.0 : advisory.getConfidence().doubleValue(),
                    parseStringList(advisory.getReasonsJson()),
                    parseStringList(advisory.getSuggestionsJson()),
                    recipients,
                    p == null ? null : p.getPmUserId(),
                    Instant.now()
            );
            eventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("[AI Advisor] publish event failed: {}", ex.getMessage());
        }
    }
}
