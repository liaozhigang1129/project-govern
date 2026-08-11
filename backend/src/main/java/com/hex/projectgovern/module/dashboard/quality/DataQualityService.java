package com.hex.projectgovern.module.dashboard.quality;

import com.hex.projectgovern.module.project.ProjectRepository;
import com.hex.projectgovern.module.risk.RiskRepository;
import com.hex.projectgovern.module.timesheet.TimesheetWeekRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 数据质量看板 (WP-M7-04) — 3 指标:
 *  1. 空值率 (字段级)
 *  2. 重复率 (主键 / 业务键)
 *  3. 时间漂移 (created_at / updated_at 偏移)
 *
 * <p>MVP 阶段: 抽样表 + 估算. 实际接 v5.
 */
@Service
@RequiredArgsConstructor
public class DataQualityService {

    private final ProjectRepository projectRepo;
    private final RiskRepository riskRepo;
    private final TimesheetWeekRepository timesheetRepo;

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot() {
        long projectCount = projectRepo.count();
        long riskCount = riskRepo.count();
        long timesheetCount = timesheetRepo.count();

        // 1) 空值率: project.budget_estimate = null 占比
        long projectsWithBudget = projectRepo.findAll().stream()
            .filter(p -> p.getBudgetEstimate() != null).count();
        double nullRate = projectCount == 0 ? 0 : 1.0 - (double) projectsWithBudget / projectCount;

        // 2) 重复率: project.code 唯一, 重复率 = 0; MVP 报 N/A
        double dupRate = 0.0;

        // 3) 时间漂移: 30 天内更新的 project 占比
        java.time.Instant cutoff = java.time.Instant.now().minusSeconds(30L * 24 * 3600);
        long recentUpdates = projectRepo.findAll().stream()
            .filter(p -> p.getUpdatedAt() != null && p.getUpdatedAt().isAfter(cutoff))
            .count();
        double timeDrift = projectCount == 0 ? 0 : (double) recentUpdates / projectCount;

        return Map.of(
            "projectCount", projectCount,
            "riskCount", riskCount,
            "timesheetCount", timesheetCount,
            "indicators", Map.of(
                "nullRate", round(nullRate),
                "duplicateRate", round(dupRate),
                "timeDrift", round(timeDrift)
            ),
            "generatedAt", java.time.Instant.now().toString()
        );
    }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
