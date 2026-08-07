package com.company.pmo.module.milestone;

import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.dict.MilestoneStatusRepository;
import com.company.pmo.module.milestone.dto.MilestoneCreateRequest;
import com.company.pmo.module.milestone.dto.MilestoneUpdateRequest;
import com.company.pmo.module.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final MilestoneStatusRepository statusRepo;
    private final ProjectRepository projectRepository;
    private final MilestonePhaseRepository milestonePhaseRepository;

    @Transactional(readOnly = true)
    public List<Milestone> listByProject(Long projectId) {
        // 用 JOIN FETCH 一次性把 status 拉进来,避免 Jackson 序列化时 LAZY 抛 no Session
        return milestoneRepository.findByProjectIdWithStatus(projectId);
    }

    /** 旧版 create(直接吃 entity)— 仅供内部/测试使用 */
    @Transactional
    public Milestone create(Milestone m) {
        if (!projectRepository.findByIdAndDeletedFalse(m.getProjectId()).isPresent()) {
            throw new BusinessException("Project not found: " + m.getProjectId());
        }
        m.setId(null);
        m.setStatus(statusRepo.findAll().stream()
                .filter(s -> "PENDING".equals(s.getCode())).findFirst()
                .orElseThrow(() -> new BusinessException("Status PENDING not seeded")));
        return milestoneRepository.save(m);
    }

    /** 新版:从 DTO 创建,weight 校验 1-10 已经在 @Valid 阶段完成 */
    @Transactional
    public Milestone createFromRequest(MilestoneCreateRequest req) {
        if (!projectRepository.findByIdAndDeletedFalse(req.projectId()).isPresent()) {
            throw new BusinessException("Project not found: " + req.projectId());
        }
        if (!milestonePhaseRepository.existsById(req.phaseId())) {
            throw new BusinessException("Phase not found: " + req.phaseId());
        }
        Milestone m = new Milestone();
        m.setProjectId(req.projectId());
        m.setName(req.name());
        m.setSequence(req.sequence());
        m.setPhaseId(req.phaseId());
        m.setPlanDate(req.planDate());
        m.setWeight(req.weight());   // 已在 @Valid 范围校验
        if (req.ownerUserId() != null) m.setOwnerUserId(req.ownerUserId());
        if (req.deliverable() != null) m.setDeliverable(req.deliverable());
        if (req.remark() != null) m.setRemark(req.remark());
        m.setStatus(statusRepo.findAll().stream()
                .filter(s -> "PENDING".equals(s.getCode())).findFirst()
                .orElseThrow(() -> new BusinessException("Status PENDING not seeded")));
        Milestone saved = milestoneRepository.save(m);
        // P1.5 收尾:新增里程碑会改变总 weight,重算项目进度
        // 旧行为下这条永远 0%(0/N),新行为下保持一致语义
        recomputeAndPersistProjectProgress(saved.getProjectId());
        // 预热 phase (避免 controller @Transactional 边界外 LAZY no Session)
        if (saved.getPhase() != null) {
            saved.getPhase().getId();
            saved.getPhase().getCode();
            saved.getPhase().getName();
        }
        return saved;
    }

    @Transactional
    public Milestone updateStatus(Long id, String newStatusCode, LocalDate actualDate) {
        Milestone m = milestoneRepository.findById(id)
                .filter(x -> !x.isDeleted())
                .orElseThrow(() -> new BusinessException(404, "Milestone not found: " + id));
        m.setStatus(statusRepo.findAll().stream()
                .filter(s -> s.getCode().equals(newStatusCode)).findFirst()
                .orElseThrow(() -> new BusinessException("Invalid status: " + newStatusCode)));
        if ("COMPLETED".equals(newStatusCode)) {
            m.setActualDate(actualDate != null ? actualDate : LocalDate.now());
            m.setCompletedAt(Instant.now());
        } else if (actualDate != null) {
            m.setActualDate(actualDate);
        }
        // P1.5 收尾:状态变更后,重算并写回 project.progressPct
        // 之前进度永远是 0,因为没人写 project.progressPct 字段
        recomputeAndPersistProjectProgress(m.getProjectId());
        // 预热 phase (避免 controller @Transactional 边界外 LAZY no Session)
        // MilestoneResponse.from() 会读 m.getPhase().getId() / getCode() / getName()
        if (m.getPhase() != null) {
            m.getPhase().getId();
            m.getPhase().getCode();
            m.getPhase().getName();
        }
        return m;
    }

    /**
     * 重算指定项目的加权进度并写回 project.progressPct。
     * <p>放在 updateStatus 末尾,确保里程碑 COMPLETED/PENDING/IN_PROGRESS/DELAYED 切换时,
     * 甘特图 bar 上的进度百分比同步刷新。</p>
     * <p>事务边界:调用方已在 @Transactional 内,所以 projectRepository.save 会顺手 flush。</p>
     */
    private void recomputeAndPersistProjectProgress(Long projectId) {
        int newPct = milestoneRepository.computeWeightedProgressPct(projectId);
        projectRepository.findByIdAndDeletedFalse(projectId).ifPresent(p -> {
            if (p.getProgressPct() != newPct) {
                p.setProgressPct(newPct);
            }
        });
    }

    @Transactional
    public void softDelete(Long id) {
        Milestone m = milestoneRepository.findById(id).orElseThrow();
        m.setDeleted(true);
        // P1.5 收尾:软删里程碑后,该 milestone 不再计入权重分母
        // 旧行为下若原本有 1 个 weight=10 已完成的,删完还是 100%,但实际分母变了
        // 新行为重算保证分母/分子同步
        recomputeAndPersistProjectProgress(m.getProjectId());
    }

    /**
     * 局部更新 — 甘特图拖拽改期用
     * <p>所有字段可选,null = 不改</p>
     * <p>注意:status / actualDate 走 updateStatus(),不混入此接口</p>
     * <p>注意:返回前预热 status(避免 controller @Transactional 边界外 LAZY no Session)</p>
     */
    @Transactional
    public Milestone updateFromRequest(Long id, MilestoneUpdateRequest req) {
        Milestone m = milestoneRepository.findById(id)
                .filter(x -> !x.isDeleted())
                .orElseThrow(() -> new BusinessException(404, "Milestone not found: " + id));

        if (req.name() != null && !req.name().isBlank()) {
            if (req.name().length() > 128) {
                throw new BusinessException("name 长度不能超过 128");
            }
            m.setName(req.name());
        }
        if (req.sequence() != null) m.setSequence(req.sequence());
        if (req.phaseId() != null) {
            if (!milestonePhaseRepository.existsById(req.phaseId())) {
                throw new BusinessException("Phase not found: " + req.phaseId());
            }
            m.setPhaseId(req.phaseId());
        }
        boolean weightChanged = false;
        if (req.planDate() != null) m.setPlanDate(req.planDate());
        if (req.weight() != null && !req.weight().equals(m.getWeight())) {
            m.setWeight(req.weight());  // 范围 1-10 已被 @Min/@Max 校验
            weightChanged = true;
        }
        if (req.ownerUserId() != null) m.setOwnerUserId(req.ownerUserId());
        if (req.deliverable() != null) m.setDeliverable(req.deliverable());
        if (req.remark() != null) m.setRemark(req.remark());

        // P1.5 收尾:weight 变化会改变分母(总 weight),必须重算
        // planDate / name / owner / deliverable / remark 不影响加权,跳过
        if (weightChanged) {
            recomputeAndPersistProjectProgress(m.getProjectId());
        }

        // 预热 status 和 phase (避免 controller @Transactional 边界外 LAZY no Session)
        // - status: PATCH /{id}/plan-date 与 PUT /{id} 都可能返回 milestone,
        //           MilestoneResponse.from() 会调 m.getStatus().getCode()
        // - phase:  PATCH /{id}/plan-date 走单字段,但前端立即再次拉取走的是该 path,
        //           同样需要 DictRef.fromPhase(m.getPhase())
        if (m.getStatus() != null) m.getStatus().getCode();
        if (m.getPhase() != null) {
            m.getPhase().getCode();
            m.getPhase().getName();
        }
        return m;
    }

    /**
     * 加权进度 = 已完成里程碑的 weight 之和 / 总 weight
     *
     * 走 repository 的 JPQL 聚合查询,一次性完成:
     *   - 不再触发 LAZY 加载 status (解决了之前 self-invocation 导致的 0% bug)
     *   - 不需要 statusRepo 二次拉字典
     *   - 空集/0 权重由 SQL 的 NULLIF + COALESCE 兜底,直接返回 0
     */
    @Transactional(readOnly = true)
    public int computeWeightedProgress(Long projectId) {
        return milestoneRepository.computeWeightedProgressPct(projectId);
    }
}
