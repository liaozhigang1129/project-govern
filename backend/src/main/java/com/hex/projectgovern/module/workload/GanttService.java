package com.hex.projectgovern.module.workload;

import com.hex.projectgovern.module.milestone.Milestone;
import com.hex.projectgovern.module.milestone.MilestoneRepository;
import com.hex.projectgovern.module.org.AppUser;
import com.hex.projectgovern.module.org.UserRepository;
import com.hex.projectgovern.module.project.Project;
import com.hex.projectgovern.module.project.ProjectRepository;
import com.hex.projectgovern.module.workload.dto.GanttDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 甘特图服务(P1.5 收尾)
 *
 *  - 数据源: project.planStartDate/planEndDate + milestone.planDate/actualDate
 *  - 范围: 显式 from/to 优先;否则 全部 bar 的 [min-7d, max+7d]
 *  - 性能: 单查询 projects + 单查询 milestones(by projectId 列表),n+1 用 map 拼
 */
@Service
@RequiredArgsConstructor
public class GanttService {

    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public GanttDtos.GanttResponse higantt(LocalDate from, LocalDate to,
                                List<Long> departmentIds, Long pmUserId, Boolean includeCompleted) {
        // 1) 项目列表 — 简单 list(前端通常不超 100)
        // departmentIds 多选:任一命中即可(null/空 = 全员)
        List<Project> projects = projectRepository.findAll().stream()
                .filter(p -> !p.isDeleted())
                .filter(p -> departmentIds == null || departmentIds.isEmpty()
                        || (p.getDepartmentId() != null && departmentIds.contains(p.getDepartmentId())))
                .filter(p -> pmUserId == null || Objects.equals(p.getPmUserId(), pmUserId))
                .filter(p -> !Boolean.FALSE.equals(includeCompleted) || p.getProgressPct() < 100)
                .toList();

        if (projects.isEmpty()) {
            // 即便没项目,也返回默认范围(前端空状态需要展示时间轴)
            LocalDate f2 = from != null ? from : LocalDate.now().minusMonths(1);
            LocalDate t2 = to   != null ? to   : f2.plusMonths(3);
            return new GanttDtos.GanttResponse(f2, t2, 0, List.of());
        }

        // 2) 拉所有相关 milestones
        List<Long> pids = projects.stream().map(Project::getId).toList();
        List<Milestone> ms = milestoneRepository.findByProjectIdInOrderByProjectIdAscSequenceAsc(pids);

        // 按 projectId 分组
        Map<Long, List<Milestone>> byPid = new HashMap<>();
        for (Milestone m : ms) {
            byPid.computeIfAbsent(m.getProjectId(), k -> new ArrayList<>()).add(m);
        }

        // 3) PM 名字(冗余展示) — 单查
        Set<Long> pmIds = new HashSet<>();
        for (Project p : projects) if (p.getPmUserId() != null) pmIds.add(p.getPmUserId());
        Map<Long, String> pmNames = new HashMap<>();
        for (AppUser u : userRepository.findAllById(pmIds)) {
            pmNames.put(u.getId(), u.getFullName());
        }

        // 4) 拼 bar
        List<GanttDtos.GanttBar> bars = new ArrayList<>(projects.size());
        LocalDate autoFrom = null, autoTo = null;
        for (Project p : projects) {
            List<Milestone> projectMs = byPid.getOrDefault(p.getId(), List.of());
            List<GanttDtos.Milestone> mvs = projectMs.stream()
                    .map(m -> new GanttDtos.Milestone(
                            m.getId(), m.getName(),
                            m.getPlanDate(), m.getActualDate(),
                            m.getStatus() == null ? null : m.getStatus().getCode(),
                            m.getWeight(),
                            // 修复: 之前误传 pmUserId 到 phaseId 字段,phaseName 永远为 null,
                            //       导致前端 PHASE_COLOR 失效,所有里程碑退化为按 status 上色,
                            //       5 个里程碑看起来就 2 种颜色 (COMPLETED×2 + IN_PROGRESS×1 + PENDING×2)。
                            //       现改为传真实 phase (V3.1 七阶段) + 阶段名,
                            //       前端可按 (phaseId, 同 phase 内的 index) 派生 28 种颜色
                            m.getPhaseId(),
                            m.getPhase() == null ? null : m.getPhase().getName()))
                    .toList();
            bars.add(new GanttDtos.GanttBar(
                    p.getId(), p.getCode(),
                    p.getName() + (pmNames.get(p.getPmUserId()) == null ? "" :
                            " [PM:" + pmNames.get(p.getPmUserId()) + "]"),
                    p.getPlanStartDate(), p.getPlanEndDate(),
                    p.getActualStartDate(), p.getActualEndDate(),
                    p.getProgressPct(), mvs));
            // 自动算范围(List.of 不接受 null,过滤一下)
            for (LocalDate d : Arrays.asList(p.getPlanStartDate(), p.getActualStartDate())) {
                if (d != null && (autoFrom == null || d.isBefore(autoFrom))) autoFrom = d;
            }
            for (LocalDate d : Arrays.asList(p.getPlanEndDate(), p.getActualEndDate())) {
                if (d != null && (autoTo == null || d.isAfter(autoTo))) autoTo = d;
            }
        }

        // 5) 范围 — 健壮性:里程碑时间窗 vs 项目时间窗 错位时,以"今天"为锚
        //
        // 旧逻辑(2026-06-08 发现):
        //   project.planStartDate=2025-01-15 → autoFrom=2025-01-08
        //   project.planEndDate  =2025-06-30 → autoTo  =2025-07-07
        //   → 浏览器时间轴 1.5 年宽,里程碑(2026)被裁掉看不见
        //
        // 新逻辑(2026-06-08 修):
        //   - 如果所有项目时间窗都落在 [today-2y, today+2y] 之外,改用"今天 ± 3 月"
        //   - 否则继续用 autoFrom/autoTo 自身
        //   - 显式 from/to 入参永远优先
        LocalDate today = LocalDate.now();
        boolean anchorOnToday = false;
        if (from == null && autoFrom != null && autoTo != null) {
            // 区间中心在 2 年外 → 视为"项目时间窗荒废",改用今天
            long centerDays = java.time.temporal.ChronoUnit.DAYS.between(autoFrom, autoTo) / 2;
            LocalDate center = autoFrom.plusDays(centerDays);
            if (center.isBefore(today.minusYears(2)) || center.isAfter(today.plusYears(2))) {
                anchorOnToday = true;
            }
        } else if (from == null && (autoFrom == null || autoTo == null)) {
            // 有项目但时间窗全空 → 用今天
            anchorOnToday = true;
        }

        LocalDate rangeFrom;
        LocalDate rangeTo;
        if (from != null && to != null) {
            rangeFrom = from;
            rangeTo = to;
        } else if (anchorOnToday) {
            rangeFrom = today.minusMonths(1);
            rangeTo = today.plusMonths(3);
        } else if (from != null) {
            rangeFrom = from;
            rangeTo = (autoTo != null ? autoTo.plusDays(7) : rangeFrom.plusMonths(3));
        } else if (to != null) {
            rangeTo = to;
            rangeFrom = (autoFrom != null ? autoFrom.minusDays(7) : rangeTo.minusMonths(3));
        } else {
            // 正常分支: 紧贴 bar 边缘 ± 7d
            rangeFrom = (autoFrom != null ? autoFrom.minusDays(7) : today);
            rangeTo   = (autoTo   != null ? autoTo.plusDays(7)   : rangeFrom.plusMonths(3));
        }
        // 兜底: rangeFrom 一定 ≤ rangeTo
        if (rangeFrom.isAfter(rangeTo)) {
            rangeFrom = rangeTo.minusMonths(3);
        }
        return new GanttDtos.GanttResponse(rangeFrom, rangeTo, bars.size(), bars);
    }
}