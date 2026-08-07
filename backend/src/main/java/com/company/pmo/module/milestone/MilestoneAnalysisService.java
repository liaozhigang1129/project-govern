package com.company.pmo.module.milestone;

import com.company.pmo.module.milestone.dto.MilestoneAnalysisQuery;
import com.company.pmo.module.milestone.dto.MilestoneAnalysisResponse;
import com.company.pmo.module.milestone.dto.MilestoneAnalysisResponse.PhaseBucket;
import com.company.pmo.module.milestone.dto.MilestoneDrillDownResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 里程碑分析 — 主视图(按 PHASE 桶 = 7 阶段) + 下钻
 *
 * V3.1 改造:
 *  - 主视图维度: 立项 / 需求 / 设计 / 开发 / 测试 / 上线运维 / 维保
 *  - 桶内带 byStatus(4 status 计数) + byName(name 命中明细)
 *  - 下钻支持 phaseId / statusCode / milestoneName 三层过滤
 *
 * <p>权限维度:
 * <ul>
 *   <li>PMO_ADMIN / EXEC / VIEWER: 全公司数据</li>
 *   <li>DEPT_LEAD: 默认限本部门, buId 限本 BU, 不可越级</li>
 *   <li>PM: 限自己是 PM 的项目, plId 强校验</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MilestoneAnalysisService {

    private final MilestoneRepository milestoneRepository;
    private final MilestonePhaseRepository milestonePhaseRepository;
    private final com.company.pmo.module.project.ProjectRepository projectRepository;
    private final com.company.pmo.module.org.UserRepository userRepository;
    private final com.company.pmo.module.org.DepartmentRepository departmentRepository;

    private static final List<String> STATUS_BUCKETS = List.of("PENDING", "IN_PROGRESS", "COMPLETED", "DELAYED");

    // ============== 周期推算 ==============
    public static LocalDate[] periodRange(String period, LocalDate from, LocalDate to) {
        if (period == null && from != null && to != null) return new LocalDate[]{from, to};
        if ("custom".equals(period)) {
            if (from == null || to == null) {
                throw new com.company.pmo.common.exception.BusinessException(400, "custom 模式必须传 from / to");
            }
            return new LocalDate[]{from, to};
        }
        LocalDate today = LocalDate.now();
        return switch (period == null ? "this_week" : period) {
            case "this_week" -> {
                LocalDate mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new LocalDate[]{mon, mon.plusDays(6)};
            }
            case "next_week" -> {
                LocalDate mon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
                yield new LocalDate[]{mon, mon.plusDays(6)};
            }
            case "this_month" -> {
                LocalDate first = today.withDayOfMonth(1);
                yield new LocalDate[]{first, first.plusMonths(1).minusDays(1)};
            }
            case "next_month" -> {
                LocalDate first = today.plusMonths(1).withDayOfMonth(1);
                yield new LocalDate[]{first, first.plusMonths(1).minusDays(1)};
            }
            default -> throw new com.company.pmo.common.exception.BusinessException(400, "不支持的 period: " + period);
        };
    }

    // ============== 项目范围解析 ==============
    public Set<Long> resolveProjectIds(String scope, Long buId, Long plId, Long viewerUserId) {
        if ("pl".equalsIgnoreCase(scope)) {
            if (plId == null) {
                return projectRepository.findByPmUserIdAndDeletedFalse(viewerUserId).stream()
                        .map(com.company.pmo.module.project.Project::getId)
                        .collect(Collectors.toSet());
            }
            return projectRepository.findByPmUserIdAndDeletedFalse(plId).stream()
                    .map(com.company.pmo.module.project.Project::getId)
                    .collect(Collectors.toSet());
        }
        if ("bu".equalsIgnoreCase(scope)) {
            if (buId == null) {
                throw new com.company.pmo.common.exception.BusinessException(400, "scope=bu 必须传 buId");
            }
            Set<Long> deptIds = collectDescendantDeptIds(buId);
            return projectRepository.findActiveByDepartmentIds(deptIds).stream()
                    .map(com.company.pmo.module.project.Project::getId)
                    .collect(Collectors.toSet());
        }
        return projectRepository.findAllActive().stream()
                .map(com.company.pmo.module.project.Project::getId)
                .collect(Collectors.toSet());
    }

    Set<Long> collectDescendantDeptIds(Long rootId) {
        Set<Long> all = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long id = stack.pop();
            if (all.add(id)) {
                departmentRepository.findAllByDeletedFalseOrderByParentIdAscSortOrderAscIdAsc()
                        .stream()
                        .filter(d -> Objects.equals(d.getParentId(), id))
                        .forEach(d -> stack.push(d.getId()));
            }
        }
        return all;
    }

    // ============== 主视图 (按 PHASE 桶) ==============
    public MilestoneAnalysisResponse analyze(MilestoneAnalysisQuery q, Long viewerUserId) {
        LocalDate[] range = periodRange(q.period(), q.from(), q.to());
        Set<Long> projectIds = resolveProjectIds(q.scope(), q.buId(), q.plId(), viewerUserId);

        // 7 阶段字典 (按 sortOrder)
        List<MilestonePhase> phases = milestonePhaseRepository.findAllByOrderBySortOrderAsc();

        if (projectIds.isEmpty()) {
            return emptyResult(q, range, phases);
        }

        List<Milestone> all = milestoneRepository.findByProjectIdWithStatus(
                new ArrayList<>(projectIds));

        // 软删 + 窗口
        List<Milestone> inWindow = all.stream()
                .filter(m -> !m.isDeleted())
                .filter(m -> !m.getPlanDate().isBefore(range[0]) && !m.getPlanDate().isAfter(range[1]))
                .toList();

        // 按 phase 聚合
        Map<Long, Long> phaseCount = new LinkedHashMap<>();
        Map<Long, Map<String, Long>> phaseByStatus = new LinkedHashMap<>();
        Map<Long, Map<String, MilestoneAnalysisResponse.NameStatusCount>> phaseByName = new LinkedHashMap<>();

        for (MilestonePhase p : phases) {
            phaseCount.put(p.getId(), 0L);
            phaseByStatus.put(p.getId(), initStatusBuckets());
            phaseByName.put(p.getId(), new LinkedHashMap<>());
        }
        for (Milestone m : inWindow) {
            Long phaseId = m.getPhaseId();
            String code = m.getStatus().getCode();
            phaseCount.merge(phaseId, 1L, Long::sum);
            phaseByStatus.get(phaseId).merge(code, 1L, Long::sum);
            // name 维度: 同名 milestone 跨项目可能不同 status, 这里聚合为 max(count) 那个 status
            String key = m.getName() + "|" + code;
            Map<String, MilestoneAnalysisResponse.NameStatusCount> bucket = phaseByName.get(phaseId);
            bucket.merge(key, new MilestoneAnalysisResponse.NameStatusCount(m.getName(), 1L, code),
                    (a, b) -> new MilestoneAnalysisResponse.NameStatusCount(a.name(), a.count() + b.count(), a.statusCode()));
        }

        // 转 NameStatusCount -> List (按 count 降序)
        Map<Long, PhaseBucket> phaseMap = new LinkedHashMap<>();
        for (MilestonePhase p : phases) {
            List<MilestoneAnalysisResponse.NameStatusCount> names = phaseByName.get(p.getId()).values().stream()
                    .sorted(Comparator.comparingLong(MilestoneAnalysisResponse.NameStatusCount::count).reversed())
                    .toList();
            phaseMap.put(p.getId(), new PhaseBucket(phaseCount.get(p.getId()), phaseByStatus.get(p.getId()), names));
        }

        // byPhase 列表 (按 sortOrder, 给前端 7 桶用)
        List<MilestoneAnalysisResponse.PhaseBucketItem> byPhase = phases.stream()
                .map(p -> new MilestoneAnalysisResponse.PhaseBucketItem(
                        p.getId(), p.getCode(), p.getName(), phaseCount.getOrDefault(p.getId(), 0L)))
                .toList();

        return new MilestoneAnalysisResponse(
                q.scope(),
                periodLabel(range[0], range[1]),
                range[0], range[1],
                (long) inWindow.size(),
                byPhase,
                phaseMap);
    }

    private MilestoneAnalysisResponse emptyResult(MilestoneAnalysisQuery q, LocalDate[] range, List<MilestonePhase> phases) {
        List<MilestoneAnalysisResponse.PhaseBucketItem> byPhase = phases.stream()
                .map(p -> new MilestoneAnalysisResponse.PhaseBucketItem(p.getId(), p.getCode(), p.getName(), 0L))
                .toList();
        Map<Long, PhaseBucket> phaseMap = new LinkedHashMap<>();
        for (MilestonePhase p : phases) {
            phaseMap.put(p.getId(), new PhaseBucket(0L, initStatusBuckets(), List.of()));
        }
        return new MilestoneAnalysisResponse(q.scope(), periodLabel(range[0], range[1]),
                range[0], range[1], 0L, byPhase, phaseMap);
    }

    private static Map<String, Long> initStatusBuckets() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String s : STATUS_BUCKETS) m.put(s, 0L);
        return m;
    }

    // ============== 下钻 ==============
    public MilestoneDrillDownResponse drillDown(MilestoneAnalysisQuery q, Long viewerUserId) {
        LocalDate[] range = periodRange(q.period(), q.from(), q.to());
        Set<Long> projectIds = resolveProjectIds(q.scope(), q.buId(), q.plId(), viewerUserId);
        if (projectIds.isEmpty()) {
            return new MilestoneDrillDownResponse(
                    q.phaseId(), null, q.milestoneId(), q.milestoneName(),
                    q.statusCode(), null, 0L, buildFilters(q, null, null, null), List.of());
        }

        List<Milestone> all = milestoneRepository.findByProjectIdWithStatus(new ArrayList<>(projectIds));
        List<Milestone> matched = all.stream()
                .filter(m -> !m.isDeleted())
                .filter(m -> !m.getPlanDate().isBefore(range[0]) && !m.getPlanDate().isAfter(range[1]))
                .filter(m -> q.phaseId() == null || Objects.equals(q.phaseId(), m.getPhaseId()))
                .filter(m -> q.statusCode() == null || q.statusCode().equals(m.getStatus().getCode()))
                .filter(m -> q.milestoneId() == null || Objects.equals(q.milestoneId(), m.getId()))
                .filter(m -> q.milestoneName() == null || q.milestoneName().equals(m.getName()))
                .toList();

        // 一次性拉项目 / 部门 / PM
        Set<Long> pjIds = matched.stream().map(Milestone::getProjectId).collect(Collectors.toSet());
        Map<Long, com.company.pmo.module.project.Project> projectMap = projectRepository.findAllById(pjIds)
                .stream().collect(Collectors.toMap(com.company.pmo.module.project.Project::getId, p -> p));
        Map<Long, com.company.pmo.module.org.Department> deptMap = departmentRepository
                .findAllByDeletedFalseOrderByParentIdAscSortOrderAscIdAsc()
                .stream().collect(Collectors.toMap(com.company.pmo.module.org.Department::getId, d -> d));
        // 一次性拉所有 PM 用户, 拼 fullName (项目 PM 缺失时给 — 占位)
        Set<Long> pmIds = projectMap.values().stream()
                .map(com.company.pmo.module.project.Project::getPmUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userMap = userRepository.findAllById(pmIds).stream()
                .collect(Collectors.toMap(com.company.pmo.module.org.AppUser::getId, u -> u.getFullName()));

        // 查 phase 字典
        String phaseName = q.phaseId() == null ? null
                : milestonePhaseRepository.findById(q.phaseId()).map(MilestonePhase::getName).orElse(null);

        // 拿第一条命中的 statusName + milestoneName (回显用)
        String statusName = matched.isEmpty() ? null : matched.get(0).getStatus().getName();
        String milestoneName = matched.isEmpty() ? q.milestoneName() : matched.get(0).getName();

        List<MilestoneDrillDownResponse.ProjectRow> rows = matched.stream()
                .map(m -> {
                    com.company.pmo.module.project.Project p = projectMap.get(m.getProjectId());
                    if (p == null) return null;
                    com.company.pmo.module.org.Department d = p.getDepartmentId() == null ? null : deptMap.get(p.getDepartmentId());
                    // BU 名: 沿 parent_id 链向上找到 root 部门
                    String buName = findRootDeptName(p.getDepartmentId(), deptMap);
                    // PM 姓名: 优先从 userMap 取 fullName, 找不到走 #user-{id} 兜底
                    String pmName = p.getPmUserId() == null ? "—"
                            : userMap.getOrDefault(p.getPmUserId(), "#user-" + p.getPmUserId());
                    // 部门名: 缺失时给 "—"
                    String deptName = d == null ? "—" : d.getName();
                    return new MilestoneDrillDownResponse.ProjectRow(
                            p.getId(), p.getCode(), p.getName(),
                            p.getBuId(), buName,
                            p.getPmUserId(), pmName,
                            p.getDepartmentId(), deptName,
                            m.getPlanDate().toString(),
                            m.getActualDate() == null ? null : m.getActualDate().toString(),
                            m.getWeight(),
                            m.getStatus().getCode(), m.getStatus().getName(),
                            m.getName());
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MilestoneDrillDownResponse.ProjectRow::planDate))
                .toList();

        return new MilestoneDrillDownResponse(
                q.phaseId(), phaseName,
                q.milestoneId(), milestoneName,
                q.statusCode(), statusName,
                rows.size(),
                buildFilters(q, phaseName, statusName, milestoneName),
                rows);
    }

    /** 沿 parent_id 链向上找到 root 部门, 拿 name 当 BU 名 */
    private String findRootDeptName(Long deptId, Map<Long, com.company.pmo.module.org.Department> deptMap) {
        if (deptId == null) return null;
        com.company.pmo.module.org.Department cur = deptMap.get(deptId);
        while (cur != null && cur.getParentId() != null) {
            cur = deptMap.get(cur.getParentId());
        }
        return cur == null ? null : cur.getName();
    }

    private String buildFilters(MilestoneAnalysisQuery q, String phaseName, String statusName, String milestoneName) {
        StringBuilder sb = new StringBuilder();
        sb.append("范围: ").append(switch (q.scope() == null ? "company" : q.scope()) {
            case "bu" -> "BU=" + (q.buId() == null ? "?" : q.buId());
            case "pl" -> "PL=" + (q.plId() == null ? "?" : q.plId());
            default -> "全公司";
        });
        if (phaseName != null) sb.append(" / 阶段=").append(phaseName);
        if (statusName != null) sb.append(" / 状态=").append(statusName);
        if (milestoneName != null) sb.append(" / 里程碑=").append(milestoneName);
        return sb.toString();
    }

    private String userNameOrId(Long id) {
        // 兜底: PM 缺失或已软删时给 — 占位
        return id == null ? "—" : ("#user-" + id);
    }

    static String periodLabel(LocalDate from, LocalDate to) {
        return from + " ~ " + to;
    }
}
