package com.company.pmo.module.resourcepipeline;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** P6 资源管道大盘 - 资源管理协同 */
@Service
@RequiredArgsConstructor
public class ResourcePipelineService {

    private final ResourceSkillRepository skillRepo;
    private final ResourcePipelineEventRepository eventRepo;

    public Map<String, Object> kpis() {
        Map<String, Object> k = new LinkedHashMap<>();
        long total = skillRepo.countDistinctUsers();
        long allocated = eventRepo.countActiveByStatus("ALLOCATED", LocalDate.now());
        long idle = eventRepo.countActiveByStatus("IDLE", LocalDate.now());
        long overloaded = eventRepo.countOverloaded(LocalDate.now());
        long skills = eventRepo.countDistinctSkills();
        long projects = eventRepo.countActiveProjects();
        double avg = eventRepo.avgAllocation() == null ? 0 : eventRepo.avgAllocation();
        k.put("totalResources", total);
        k.put("allocated", allocated);
        k.put("idle", idle);
        k.put("overloaded", overloaded);
        k.put("utilization", allocated * 100.0 / Math.max(1, allocated + idle));
        k.put("totalSkills", skills);
        k.put("activeProjects", projects);
        k.put("avgAllocation", Math.round(avg * 10) / 10.0);
        return k;
    }

    public Map<String, Object> capacityMatrix(LocalDate from, LocalDate to) {
        List<Object[]> rows = eventRepo.aggregateByUserAndWeek(from, to);
        List<LocalDate> weekList = new ArrayList<>();
        Map<Long, Map<String, Object>> userMap = new LinkedHashMap<>();
        for (Object[] r : rows) {
            Long uid = ((Number) r[0]).longValue();
            // PG driver 返回 java.sql.Date; PG 端 ::date cast 也是 java.sql.Date; 兼容 LocalDate 已是 PG 14+
            LocalDate week;
            Object wk = r[1];
            if (wk instanceof java.sql.Date sqlDate) {
                week = sqlDate.toLocalDate();
            } else if (wk instanceof LocalDate ld) {
                week = ld;
            } else if (wk instanceof java.sql.Timestamp ts) {
                week = ts.toLocalDateTime().toLocalDate();
            } else {
                week = LocalDate.parse(wk.toString());
            }
            BigDecimal alloc = (BigDecimal) r[2];
            if (!weekList.contains(week)) weekList.add(week);
            userMap.computeIfAbsent(uid, k -> {
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("userId", uid);
                u.put("weeks", new LinkedHashMap<String, Object>());
                return u;
            });
            Map<String, Object> weeks = (Map<String, Object>) userMap.get(uid).get("weeks");
            weeks.put(week.toString(), Map.of(
                "allocPct", alloc,
                "actualHrs", 0,
                "overload", alloc.doubleValue() > 100.0
            ));
        }
        Collections.sort(weekList);
        return Map.of(
            "from", from.toString(),
            "to", to.toString(),
            "weeks", weekList.stream().map(LocalDate::toString).toList(),
            "users", new ArrayList<>(userMap.values())
        );
    }

    public List<Map<String, Object>> skillMatrix() {
        List<Object[]> rows = skillRepo.aggregateSkillStats();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            String code = (String) r[0];
            Long count = ((Number) r[1]).longValue();
            Double avgLevel = ((Number) r[2]).doubleValue();
            Long certified = ((Number) r[3]).longValue();
            result.add(Map.of(
                "skillCode", code,
                "count", count,
                "avgLevel", Math.round(avgLevel * 10) / 10.0,
                "certified", certified
            ));
        }
        return result;
    }

    public List<Map<String, Object>> overloadAlerts() {
        List<Object[]> rows = eventRepo.findOverloadAlerts(LocalDate.now());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(Map.of(
                "userId", r[0],
                "userName", r[1] != null ? r[1].toString() : "User#" + r[0],
                "departmentId", r[2],
                "departmentName", r[3] != null ? r[3].toString() : "",
                "allocSum", r[4],
                "projectCount", r[5]
            ));
        }
        return result;
    }

    public List<Map<String, Object>> deptCapacity() {
        List<Object[]> rows = eventRepo.aggregateByDepartment();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(Map.of(
                "departmentId", r[0],
                "departmentName", r[1] != null ? r[1].toString() : "未分配",
                "headCount", r[2],
                "totalAllocation", r[3]
            ));
        }
        return result;
    }
}
