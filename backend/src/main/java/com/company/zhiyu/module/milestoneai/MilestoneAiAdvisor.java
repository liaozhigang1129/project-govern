package com.company.zhiyu.module.milestoneai;

import com.company.zhiyu.module.milestone.Milestone;
import com.company.zhiyu.module.milestone.MilestonePhase;
import com.company.zhiyu.module.project.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 5 维信号 + 评分引擎 (纯函数, 零副作用) */
@Component
public class MilestoneAiAdvisor {
    private final ObjectMapper mapper;
    public MilestoneAiAdvisor(ObjectMapper mapper) { this.mapper = mapper; }

    public AdviceResult analyze(Project project, Milestone m, MilestonePhase phase,
                                Double spi, Integer phaseLagDays, Double velocityDeltaPct,
                                Double historicalHitRate) {
        List<Signal> signals = new ArrayList<>(5);
        signals.add(scoreOverdue(m));
        signals.add(scoreSpi(spi));
        signals.add(scorePhaseLag(m, phaseLagDays));
        signals.add(scoreVelocity(velocityDeltaPct));
        signals.add(scoreHistorical(historicalHitRate));
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (Signal s : signals) {
            totalScore = totalScore.add(s.score());
            totalWeight = totalWeight.add(s.weight());
        }
        BigDecimal confidence = totalWeight.compareTo(BigDecimal.ZERO) == 0
                ? new BigDecimal("0.50")
                : BigDecimal.ONE.subtract(BigDecimal.ONE.divide(totalWeight.add(BigDecimal.ONE), 2, RoundingMode.HALF_UP));
        String severity = toSeverity(totalScore);
        int probability = toProbability(totalScore);
        int impact = toImpact(phase, severity);
        String category = "SCHEDULE";
        String fingerprint = buildFingerprint(project.getId(), m.getId(), severity, totalScore);
        ArrayNode reasons = mapper.createArrayNode();
        ArrayNode suggestions = mapper.createArrayNode();
        for (Signal s : signals) {
            if (s.intensity().compareTo(new BigDecimal("30")) > 0) {
                reasons.add(s.description());
            }
        }
        for (Signal s : signals) {
            if (s.score().compareTo(new BigDecimal("20")) > 0) {
                ObjectNode sug = mapper.createObjectNode();
                sug.put("signal", s.type());
                sug.put("action", suggest(s.type(), m));
                suggestions.add(sug);
            }
        }
        if (reasons.size() == 0) reasons.add("无显著风险信号");
        if (suggestions.size() == 0) suggestions.add("持续监控");
        return new AdviceResult(signals, totalScore.setScale(2, RoundingMode.HALF_UP),
                confidence, severity, category, probability, impact, fingerprint, reasons, suggestions);
    }

    Signal scoreOverdue(Milestone m) {
        if (m.getStatus() == null || !isTerminal(m) && m.getPlanDate() == null) {
            return new Signal("OVERDUE", new BigDecimal("0.00"), new BigDecimal("0.30"), new BigDecimal("0.00"), "无计划日期", true);
        }
        if (isTerminal(m)) {
            return new Signal("OVERDUE", new BigDecimal("0.00"), new BigDecimal("0.30"), new BigDecimal("0.00"), "里程碑已完成", false);
        }
        long days = ChronoUnit.DAYS.between(m.getPlanDate(), LocalDate.now());
        if (days <= 0) {
            return new Signal("OVERDUE", new BigDecimal("10.00"), new BigDecimal("0.30"), new BigDecimal("3.00"), "未到计划日期", false);
        }
        BigDecimal intensity = days >= 30 ? new BigDecimal("100.00") : new BigDecimal(days * 100 / 30).setScale(2, RoundingMode.HALF_UP);
        BigDecimal weight = new BigDecimal("0.30");
        BigDecimal score = intensity.multiply(weight).setScale(2, RoundingMode.HALF_UP);
        return new Signal("OVERDUE", intensity, weight, score, "已逾期 " + days + " 天", false);
    }

    Signal scoreSpi(Double spi) {
        if (spi == null) return new Signal("SPI", new BigDecimal("0.00"), new BigDecimal("0.20"), new BigDecimal("0.00"), "无 SPI 数据", true);
        BigDecimal s = BigDecimal.valueOf(spi);
        BigDecimal intensity = s.compareTo(BigDecimal.ONE) >= 0 ? new BigDecimal("0.00") : BigDecimal.ONE.subtract(s).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal weight = new BigDecimal("0.20");
        BigDecimal score = intensity.multiply(weight).setScale(2, RoundingMode.HALF_UP);
        return new Signal("SPI", intensity, weight, score, String.format("SPI=%.2f", spi), false);
    }

    Signal scorePhaseLag(Milestone m, Integer phaseLagDays) {
        if (phaseLagDays == null) return new Signal("PHASE_LAG", new BigDecimal("0.00"), new BigDecimal("0.20"), new BigDecimal("0.00"), "无 phase lag 数据", true);
        BigDecimal intensity = phaseLagDays >= 14 ? new BigDecimal("100.00") : new BigDecimal(phaseLagDays * 100 / 14).setScale(2, RoundingMode.HALF_UP);
        BigDecimal weight = new BigDecimal("0.20");
        BigDecimal score = intensity.multiply(weight).setScale(2, RoundingMode.HALF_UP);
        return new Signal("PHASE_LAG", intensity, weight, score, "phase 滞后 " + phaseLagDays + " 天", false);
    }

    Signal scoreVelocity(Double velocityDeltaPct) {
        if (velocityDeltaPct == null) return new Signal("VELOCITY", new BigDecimal("0.00"), new BigDecimal("0.15"), new BigDecimal("0.00"), "无 velocity 数据", true);
        double d = Math.max(0, -velocityDeltaPct);
        BigDecimal intensity = d >= 50 ? new BigDecimal("100.00") : new BigDecimal(d * 2).setScale(2, RoundingMode.HALF_UP);
        BigDecimal weight = new BigDecimal("0.15");
        BigDecimal score = intensity.multiply(weight).setScale(2, RoundingMode.HALF_UP);
        return new Signal("VELOCITY", intensity, weight, score, String.format("velocity 变化 %.1f%%", velocityDeltaPct), false);
    }

    Signal scoreHistorical(Double historicalHitRate) {
        if (historicalHitRate == null) return new Signal("HISTORICAL", new BigDecimal("0.00"), new BigDecimal("0.15"), new BigDecimal("0.00"), "无历史数据", true);
        double inv = 1.0 - Math.max(0, Math.min(1, historicalHitRate));
        BigDecimal intensity = new BigDecimal(inv * 100).setScale(2, RoundingMode.HALF_UP);
        BigDecimal weight = new BigDecimal("0.15");
        BigDecimal score = intensity.multiply(weight).setScale(2, RoundingMode.HALF_UP);
        return new Signal("HISTORICAL", intensity, weight, score, String.format("历史命中率 %.1f%%", historicalHitRate * 100), false);
    }

    String toSeverity(BigDecimal score) {
        if (score.compareTo(new BigDecimal("60")) >= 0) return "CRITICAL";
        if (score.compareTo(new BigDecimal("30")) >= 0) return "WARNING";
        return "INFO";
    }
    int toProbability(BigDecimal score) {
        if (score.compareTo(new BigDecimal("60")) >= 0) return 4;
        if (score.compareTo(new BigDecimal("30")) >= 0) return 3;
        if (score.compareTo(new BigDecimal("15")) >= 0) return 2;
        return 1;
    }
    int toImpact(MilestonePhase phase, String severity) {
        boolean highPhase = phase != null && Set.of("DEV", "TEST", "UAT", "GOLIVE").contains(phase.getCode());
        if ("CRITICAL".equals(severity)) return highPhase ? 5 : 4;
        if ("WARNING".equals(severity)) return highPhase ? 4 : 3;
        return highPhase ? 2 : 1;
    }
    String suggest(String type, Milestone m) {
        return switch (type) {
            case "OVERDUE" -> "立即复盘原因,调整计划或拆分细化";
            case "SPI" -> "增加资源/并行任务,加速执行";
            case "PHASE_LAG" -> "检查上游依赖,识别阻塞点";
            case "VELOCITY" -> "识别效率下降原因,采取纠偏行动";
            case "HISTORICAL" -> "参考历史项目教训,提前干预";
            default -> "持续监控";
        };
    }
    String buildFingerprint(Long projectId, Long milestoneId, String severity, BigDecimal score) {
        String s = projectId + ":" + milestoneId + ":" + severity + ":" + score.setScale(0, RoundingMode.HALF_UP);
        return Integer.toHexString(s.hashCode());
    }
    boolean isTerminal(Milestone m) {
        return m.getStatus() != null && m.getStatus().isTerminal();
    }

    record Signal(String type, BigDecimal intensity, BigDecimal weight, BigDecimal score, String description, boolean missing) {}
    record AdviceResult(List<Signal> signals, BigDecimal score, BigDecimal confidence,
                        String severity, String category, int suggestedProbability,
                        int suggestedImpact, String fingerprint, com.fasterxml.jackson.databind.JsonNode reasons,
                        com.fasterxml.jackson.databind.JsonNode suggestions) {}
}
