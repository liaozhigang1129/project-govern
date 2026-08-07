package com.hex.projectgovern.module.dashboard;

import com.hex.projectgovern.module.dict.BusinessUnit;
import com.hex.projectgovern.module.dict.BusinessUnitRepository;
import com.hex.projectgovern.module.dict.ProductLine;
import com.hex.projectgovern.module.dict.ProductLineRepository;
import com.hex.projectgovern.module.initiation.ProjectInitiationRepository;
import com.hex.projectgovern.module.project.Project;
import com.hex.projectgovern.module.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final ProjectInitiationRepository initiationRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final ProductLineRepository productLineRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> kpis() {
        List<Project> all = projectRepository.findAllActive();
        LocalDate today = LocalDate.now();
        java.time.YearMonth thisMonth = java.time.YearMonth.from(today);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activeCount", all.stream().filter(p -> "ACTIVE".equals(p.getStatus().getCode())).count());
        m.put("newInitiationsThisMonth", initiationRepository.findByDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(i -> i.getCreatedAt() != null) // @CreatedDate 在 @DataJpaTest 默认未启用,可能为 null
                .filter(i -> {
                    java.time.YearMonth ym = java.time.YearMonth.from(
                            i.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
                    return ym.equals(thisMonth);
                }).count());
        m.put("closedThisMonth", all.stream()
                .filter(p -> "CLOSED".equals(p.getStatus().getCode())
                        && p.getActualEndDate() != null
                        && p.getActualEndDate().getMonthValue() == today.getMonthValue()
                        && p.getActualEndDate().getYear() == today.getYear()).count());
        m.put("overdueProjects", all.stream()
                .filter(p -> "ACTIVE".equals(p.getStatus().getCode())
                        && p.getPlanEndDate() != null
                        && p.getPlanEndDate().isBefore(today)).count());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> statusDistribution() {
        return projectRepository.findAllActive().stream()
                .filter(p -> p.getStatus() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        p -> p.getStatus().getName(), java.util.stream.Collectors.counting()));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> healthDistribution() {
        return projectRepository.findAllActive().stream()
                .filter(p -> p.getHealth() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        p -> p.getHealth().getName(), java.util.stream.Collectors.counting()));
    }

    @Transactional(readOnly = true)
    public List<com.hex.projectgovern.module.dashboard.dto.ProjectCardDto> activeProjects() {
        return projectRepository.findAllActive().stream()
                .map(com.hex.projectgovern.module.dashboard.dto.ProjectCardDto::from)
                .toList();
    }

    // ====== BU/PL 维度统计 ======

    /**
     * 按业务单元(BU)分组:项目数量 + 平均进度
     * 返回形如 [{ buName, buCode, projectCount, avgProgress }]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> buDistribution() {
        List<Project> all = projectRepository.findAllActive();
        List<BusinessUnit> bus = businessUnitRepository.findAllByDeletedFalseOrderBySortOrderAscIdAsc();

        // 按 buId 分组
        Map<Long, List<Project>> grouped = new LinkedHashMap<>();
        for (BusinessUnit bu : bus) {
            grouped.put(bu.getId(), new ArrayList<>());
        }
        // 未分配 BU 的项目也收集(键=0)
        grouped.put(0L, new ArrayList<>());

        for (Project p : all) {
            long buId = p.getBuId() != null ? p.getBuId() : 0L;
            grouped.computeIfAbsent(buId, k -> new ArrayList<>()).add(p);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BusinessUnit bu : bus) {
            List<Project> projects = grouped.getOrDefault(bu.getId(), List.of());
            double avg = projects.isEmpty() ? 0.0
                    : projects.stream().mapToInt(Project::getProgressPct).average().orElse(0.0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("buId", bu.getId());
            row.put("buName", bu.getName());
            row.put("buCode", bu.getCode());
            row.put("projectCount", projects.size());
            row.put("avgProgress", Math.round(avg * 10.0) / 10.0);
            result.add(row);
        }
        // 未分配
        List<Project> unassigned = grouped.getOrDefault(0L, List.of());
        if (!unassigned.isEmpty()) {
            double avg = unassigned.stream().mapToInt(Project::getProgressPct).average().orElse(0.0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("buId", 0L);
            row.put("buName", "未分配");
            row.put("buCode", "UNASSIGNED");
            row.put("projectCount", unassigned.size());
            row.put("avgProgress", Math.round(avg * 10.0) / 10.0);
            result.add(row);
        }
        return result;
    }

    /**
     * 按产品线(PL)分组:项目数量 + 平均进度
     * 返回形如 [{ plName, plCode, buName, projectCount, avgProgress }]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> plDistribution() {
        List<Project> all = projectRepository.findAllActive();
        List<ProductLine> pls = productLineRepository.findAllByDeletedFalseOrderBySortOrderAscIdAsc();

        // buId -> buName 映射
        Map<Long, String> buNameMap = new HashMap<>();
        businessUnitRepository.findAllByDeletedFalseOrderBySortOrderAscIdAsc()
                .forEach(bu -> buNameMap.put(bu.getId(), bu.getName()));

        // 按 plId 分组
        Map<Long, List<Project>> grouped = new LinkedHashMap<>();
        for (ProductLine pl : pls) {
            grouped.put(pl.getId(), new ArrayList<>());
        }
        grouped.put(0L, new ArrayList<>());

        for (Project p : all) {
            long plId = p.getPlId() != null ? p.getPlId() : 0L;
            grouped.computeIfAbsent(plId, k -> new ArrayList<>()).add(p);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ProductLine pl : pls) {
            List<Project> projects = grouped.getOrDefault(pl.getId(), List.of());
            double avg = projects.isEmpty() ? 0.0
                    : projects.stream().mapToInt(Project::getProgressPct).average().orElse(0.0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("plId", pl.getId());
            row.put("plName", pl.getName());
            row.put("plCode", pl.getCode());
            row.put("buName", buNameMap.getOrDefault(pl.getBu() != null ? pl.getBu().getId() : 0L, "—"));
            row.put("projectCount", projects.size());
            row.put("avgProgress", Math.round(avg * 10.0) / 10.0);
            result.add(row);
        }
        // 未分配
        List<Project> unassigned = grouped.getOrDefault(0L, List.of());
        if (!unassigned.isEmpty()) {
            double avg = unassigned.stream().mapToInt(Project::getProgressPct).average().orElse(0.0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("plId", 0L);
            row.put("plName", "未分配");
            row.put("plCode", "UNASSIGNED");
            row.put("buName", "—");
            row.put("projectCount", unassigned.size());
            row.put("avgProgress", Math.round(avg * 10.0) / 10.0);
            result.add(row);
        }
        return result;
    }
}

