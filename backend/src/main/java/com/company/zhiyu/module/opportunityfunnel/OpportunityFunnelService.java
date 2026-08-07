package com.company.zhiyu.module.opportunityfunnel;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/** P6 商机漏斗大盘 - 商机配置 */
@Service
@RequiredArgsConstructor
public class OpportunityFunnelService {

    private final OpportunityRepository opportunityRepo;

    public Map<String, Object> kpis() {
        Map<String, Object> k = new LinkedHashMap<>();
        long totalOpen = opportunityRepo.countByStatusAndDeletedFalse("OPEN");
        long totalWon = opportunityRepo.countByStageAndDeletedFalse("WON");
        long totalLost = opportunityRepo.countByStageAndDeletedFalse("LOST");
        long totalAll = opportunityRepo.countAll();
        BigDecimal totalAmount = opportunityRepo.sumAmountByStatus("OPEN");
        BigDecimal wonAmount = opportunityRepo.sumAmountByStage("WON");
        BigDecimal wonAmountAll = opportunityRepo.sumAmountWon();
        BigDecimal weighted = opportunityRepo.sumWeightedAmount();
        long buCount = opportunityRepo.countDistinctBu();
        double winRate = (totalWon + totalLost) > 0
            ? Math.round(totalWon * 1000.0 / (totalWon + totalLost)) / 10.0
            : 0.0;
        double avgDealSize = totalWon > 0
            ? Math.round(wonAmountAll.doubleValue() / totalWon / 100.0) / 100.0
            : 0.0;
        k.put("openCount", totalOpen);
        k.put("wonCount", totalWon);
        k.put("lostCount", totalLost);
        k.put("openAmount", totalAmount);
        k.put("wonAmount", wonAmount);
        k.put("winRate", winRate);
        k.put("weightedPipeline", weighted);
        k.put("totalOpportunities", totalAll);
        k.put("buCount", buCount);
        k.put("avgDealSize", avgDealSize);
        return k;
    }

    public List<Map<String, Object>> funnel() {
        String[] stages = {"LEAD", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "WON", "LOST"};
        List<Map<String, Object>> result = new ArrayList<>();
        for (String stage : stages) {
            long count = opportunityRepo.countByStageAndDeletedFalse(stage);
            BigDecimal amount = opportunityRepo.sumAmountByStage(stage);
            result.add(Map.of(
                "stage", stage,
                "count", count,
                "amount", amount,
                "color", stageColor(stage)
            ));
        }
        return result;
    }

    private String stageColor(String stage) {
        return switch (stage) {
            case "LEAD" -> "#909399";
            case "QUALIFIED" -> "#67c23a";
            case "PROPOSAL" -> "#409eff";
            case "NEGOTIATION" -> "#e6a23c";
            case "WON" -> "#67c23a";
            case "LOST" -> "#f56c6c";
            default -> "#909399";
        };
    }

    public List<Map<String, Object>> conversionRates() {
        long lead = opportunityRepo.countByStageEver("LEAD");
        long qualified = opportunityRepo.countByStageEver("QUALIFIED");
        long proposal = opportunityRepo.countByStageEver("PROPOSAL");
        long negotiation = opportunityRepo.countByStageEver("NEGOTIATION");
        long won = opportunityRepo.countByStageEver("WON");

        return List.of(
            Map.of("from", "LEAD", "to", "QUALIFIED", "rate", safeRate(qualified, lead)),
            Map.of("from", "QUALIFIED", "to", "PROPOSAL", "rate", safeRate(proposal, qualified)),
            Map.of("from", "PROPOSAL", "to", "NEGOTIATION", "rate", safeRate(negotiation, proposal)),
            Map.of("from", "NEGOTIATION", "to", "WON", "rate", safeRate(won, negotiation))
        );
    }

    private double safeRate(long a, long b) {
        if (b == 0) return 0;
        return Math.round(a * 1000.0 / b) / 10.0;
    }

    public List<Map<String, Object>> monthlyTrend() {
        List<Object[]> rows = opportunityRepo.aggregateMonthlyWon();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(Map.of(
                "month", r[0] != null ? r[0].toString() : "",
                "wonCount", r[1],
                "wonAmount", r[2]
            ));
        }
        return result;
    }

    public List<Map<String, Object>> salesRank() {
        List<Object[]> rows = opportunityRepo.aggregateByOwner();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(Map.of(
                "userId", r[0],
                "userName", r[1] != null ? r[1].toString() : "User#" + r[0],
                "openCount", r[2],
                "wonCount", r[3],
                "wonAmount", r[4]
            ));
        }
        return result;
    }

    public List<Map<String, Object>> amountByBuPl() {
        List<Object[]> rows = opportunityRepo.aggregateByBuPl();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(Map.of(
                "buId", r[0],
                "buName", r[1] != null ? r[1].toString() : "未分配",
                "plId", r[2],
                "plName", r[3] != null ? r[3].toString() : "未分配",
                "openAmount", r[4],
                "wonAmount", r[5],
                "wonCount", r[6]
            ));
        }
        return result;
    }
}
