package com.hex.projectgovern.module.healthadvisor;

import com.hex.projectgovern.common.api.ApiResponse;
import com.hex.projectgovern.module.dict.HealthLevel;
import com.hex.projectgovern.module.dict.HealthLevelRepository;
import com.hex.projectgovern.module.milestone.Milestone;
import com.hex.projectgovern.module.milestone.MilestoneRepository;
import com.hex.projectgovern.module.project.Project;
import com.hex.projectgovern.module.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 健康度建议服务。
 *
 * 两种用法:
 *  1. suggestForProject(id) — 单项目 dry-run,返回建议但不写库
 *  2. runForAll() / runForAll(boolean apply) — 全 ACTIVE 项目跑批,可选择是否回写
 *
 * 计算逻辑全在 HealthAdvisor(纯函数)里,本类负责:
 *  - 拉数据(项目 + 它的 milestones)
 *  - 把 code 字符串反查回 HealthLevel 实体
 *  - apply 模式下写回 project.health
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealthAdvisorService {

    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final HealthLevelRepository healthLevelRepository;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Transactional(readOnly = true)
    public HealthSuggestion suggestForProject(Long projectId) {
        Project p = projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在: id=" + projectId));
        return computeSuggestion(p);
    }

    @Transactional
    public List<HealthSuggestion> runForAll(boolean apply) {
        List<Project> projects = projectRepository.findAllActiveProjects();
        log.info("[HealthAdvisor] 跑批开始: 共 {} 个 ACTIVE 项目,apply={}", projects.size(), apply);
        List<HealthSuggestion> out = new ArrayList<>(projects.size());
        int written = 0;
        for (Project p : projects) {
            HealthSuggestion s = computeSuggestion(p);
            out.add(s);
            if (apply && s.getSuggestedCode() != null) {
                HealthLevel hl = healthLevelRepository.findByCode(s.getSuggestedCode())
                        .orElse(null);
                if (hl != null && (p.getHealth() == null || !hl.getCode().equals(p.getHealth().getCode()))) {
                    p.setHealth(hl);
                    projectRepository.save(p);
                    written++;
                }
            }
        }
        log.info("[HealthAdvisor] 跑批完成: 评估 {} 个,回写 {} 个", projects.size(), written);
        return out;
    }

    private HealthSuggestion computeSuggestion(Project p) {
        List<Milestone> milestones = milestoneRepository.findByProjectIdWithStatus(p.getId());
        return HealthAdvisor.compute(p, milestones, LocalDate.now(ZONE), ZONE);
    }
}
