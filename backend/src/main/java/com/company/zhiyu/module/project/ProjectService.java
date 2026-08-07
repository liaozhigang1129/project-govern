package com.company.zhiyu.module.project;

import com.company.zhiyu.common.exception.BusinessException;
import com.company.zhiyu.module.dashboard.dto.ProjectCardDto;
import com.company.zhiyu.module.dict.*;
import com.company.zhiyu.module.org.AppUser;
import com.company.zhiyu.module.org.UserRepository;
import com.company.zhiyu.module.project.dto.*;
import com.company.zhiyu.module.project.dto.ProjectDetailResponse.DictRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectTypeRepository typeRepo;
    private final ProjectStatusRepository statusRepo;
    private final HealthLevelRepository healthRepo;
    private final BusinessUnitRepository buRepo;
    private final ProductLineRepository plRepo;
    private final RelatedProductRepository rpRepo;
    private final UserRepository userRepo;
    private final com.company.zhiyu.module.member.ProjectMemberService memberService;

    // ===== 列表(无变化) =====
    public List<Project> list() { return projectRepository.findAllActive(); }

    /**
     * 多条件搜索 + 字段补全(BU/PL/PM 姓名)
     *  - 入参 ProjectQuery 字段全可选,null 视为不过滤
     *  - 字典子查询:批量拉 BU/PL/产品/用户 → Map,避免 N+1
     */
    @Transactional(readOnly = true)
    public List<ProjectCardDto> listCards() {
        return enrichCards(projectRepository.findAllActive());
    }

    @Transactional(readOnly = true)
    public List<ProjectCardDto> searchCards(ProjectQuery q) {
        return enrichCards(projectRepository.findAll(ProjectRepository.specOf(q)));
    }

    /** 公共组装:批量拉字典/用户,避免 N+1 */
    private List<ProjectCardDto> enrichCards(List<Project> projects) {
        if (projects.isEmpty()) return List.of();

        // 收集所有 id
        Set<Long> buIds  = projects.stream().map(Project::getBuId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> plIds  = projects.stream().map(Project::getPlId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> rpIds  = projects.stream().map(Project::getRelatedProductId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> pmIds  = projects.stream().map(Project::getPmUserId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        // 批量查字典
        Map<Long, BusinessUnit>  bus = buIds.isEmpty() ? Map.of()
                : buRepo.findAllById(buIds).stream().collect(Collectors.toMap(BusinessUnit::getId, b -> b));
        Map<Long, ProductLine>   pls = plIds.isEmpty() ? Map.of()
                : plRepo.findAllById(plIds).stream().collect(Collectors.toMap(ProductLine::getId, p -> p));
        Map<Long, RelatedProduct> rps = rpIds.isEmpty() ? Map.of()
                : rpRepo.findAllById(rpIds).stream().collect(Collectors.toMap(RelatedProduct::getId, r -> r));
        Map<Long, AppUser>       pms = pmIds.isEmpty() ? Map.of()
                : userRepo.findAllById(pmIds).stream().collect(Collectors.toMap(AppUser::getId, u -> u));

        return projects.stream().map(p -> {
            ProjectCardDto base = ProjectCardDto.from(p);
            // 强制初始化 LAZY(若 p 是从 JPA 来的)
            if (p.getType() != null)   p.getType().getName();
            if (p.getStatus() != null) p.getStatus().getName();
            if (p.getHealth() != null) p.getHealth().getName();

            DictRef buRef  = null, plRef = null, rpRef = null;
            if (p.getBuId() != null && bus.containsKey(p.getBuId())) {
                BusinessUnit b = bus.get(p.getBuId());
                buRef = new DictRef(b.getId(), b.getCode(), b.getName());
            }
            if (p.getPlId() != null && pls.containsKey(p.getPlId())) {
                ProductLine pl = pls.get(p.getPlId());
                plRef = new DictRef(pl.getId(), pl.getCode(), pl.getName());
                plRef.parentId = pl.getBu() != null ? pl.getBu().getId() : null;
            }
            if (p.getRelatedProductId() != null && rps.containsKey(p.getRelatedProductId())) {
                RelatedProduct r = rps.get(p.getRelatedProductId());
                DictRef rr = new DictRef(r.getId(), r.getCode(), r.getName());
                rr.parentId = r.getPl() != null ? r.getPl().getId() : null;
                rr.version = r.getVersion();
                rpRef = rr;
            }
            String pmName = p.getPmUserId() != null && pms.containsKey(p.getPmUserId())
                    ? pms.get(p.getPmUserId()).getFullName() : null;
            return new ProjectCardDto(
                    base.id(), base.code(), base.name(), base.customer(),
                    base.type(), base.status(), base.health(),
                    buRef, plRef, rpRef,
                    base.pmUserId(), pmName,
                    base.planStartDate(), base.planEndDate(),
                    base.progressPct(), base.budgetEstimate(), base.updatedAt()
            );
        }).toList();
    }

    // ===== 旧版 get(返回实体,可能有 LAZY 问题) — 保留向后兼容 =====
    @Transactional(readOnly = true)
    public Project get(Long id) {
        return projectRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "Project not found: " + id));
    }

    // ===== 新版:用 DTO 返回,事务内完成所有映射 =====
    @Transactional(readOnly = true)
    public ProjectDetailResponse getDetail(Long id) {
        Project p = projectRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "Project not found: " + id));
        // 强制初始化 LAZY 关联(在事务内,避免反序列化时 Session 关闭)
        if (p.getType() != null) p.getType().getName();
        if (p.getStatus() != null) p.getStatus().getName();
        if (p.getHealth() != null) p.getHealth().getName();
        ProjectDetailResponse d = ProjectDetailResponse.from(p);

        // 补 BU/PL/RP/PM 嵌套展示
        if (p.getBuId() != null) {
            buRepo.findById(p.getBuId()).ifPresent(b -> {
                ProjectDetailResponse.DictRef r = new ProjectDetailResponse.DictRef(b.getId(), b.getCode(), b.getName());
                d.bu = r;
            });
        }
        if (p.getPlId() != null) {
            plRepo.findById(p.getPlId()).ifPresent(pl -> {
                ProjectDetailResponse.DictRef r = new ProjectDetailResponse.DictRef(pl.getId(), pl.getCode(), pl.getName());
                r.parentId = pl.getBu() != null ? pl.getBu().getId() : null;
                d.pl = r;
            });
        }
        if (p.getRelatedProductId() != null) {
            rpRepo.findById(p.getRelatedProductId()).ifPresent(rp -> {
                ProjectDetailResponse.DictRef r = new ProjectDetailResponse.DictRef(rp.getId(), rp.getCode(), rp.getName());
                r.parentId = rp.getPl() != null ? rp.getPl().getId() : null;
                r.version = rp.getVersion();
                d.relatedProduct = r;
            });
        }
        if (p.getPmUserId() != null) {
            userRepo.findById(p.getPmUserId()).ifPresent(u -> d.pmUserName = u.getFullName());
        }
        return d;
    }

    // ===== 旧版 create(用 Project 实体)— 保留向后兼容 =====
    @Transactional
    public Project create(Project p) {
        if (projectRepository.existsByCodeAndDeletedFalse(p.getCode())) {
            throw new BusinessException("Project code exists: " + p.getCode());
        }
        p.setId(null);
        p.setType(typeRepo.findById(p.getType().getId())
                .orElseThrow(() -> new BusinessException("Invalid type")));
        p.setStatus(statusRepo.findById(p.getStatus().getId())
                .orElseThrow(() -> new BusinessException("Invalid status")));
        if (p.getHealth() != null && p.getHealth().getId() != null) {
            p.setHealth(healthRepo.findById(p.getHealth().getId()).orElse(null));
        }
        return projectRepository.save(p);
    }

    // ===== 新版:用 DTO 接收,code 字符串 → entity 转换 =====
    @Transactional
    public ProjectDetailResponse createFromRequest(ProjectCreateRequest req) {
        if (projectRepository.existsByCodeAndDeletedFalse(req.getCode())) {
            throw new BusinessException("Project code exists: " + req.getCode());
        }
        ProjectType type = typeRepo.findByCode(req.getTypeCode())
                .orElseThrow(() -> new BusinessException("Unknown typeCode: " + req.getTypeCode()));
        ProjectStatus status = statusRepo.findByCode(req.getStatusCode())
                .orElseThrow(() -> new BusinessException("Unknown statusCode: " + req.getStatusCode()));

        Project p = new Project();
        p.setCode(req.getCode());
        p.setName(req.getName());
        p.setType(type);
        p.setStatus(status);

        if (req.getHealthCode() != null && !req.getHealthCode().isBlank()) {
            HealthLevel h = healthRepo.findByCode(req.getHealthCode())
                    .orElseThrow(() -> new BusinessException("Unknown healthCode: " + req.getHealthCode()));
            p.setHealth(h);
        }

        p.setCustomer(req.getCustomer());
        p.setDepartmentId(req.getDepartmentId());
        p.setPmUserId(req.getPmUserId());
        p.setSponsorUserId(req.getSponsorUserId());
        p.setBuId(req.getBuId());
        p.setPlId(req.getPlId());
        p.setRelatedProductId(req.getRelatedProductId());
        p.setDescription(req.getDescription());
        p.setBackground(req.getBackground());
        p.setGoals(req.getGoals());
        p.setScope(req.getScope());
        p.setPlanStartDate(req.getPlanStartDate());
        p.setPlanEndDate(req.getPlanEndDate());
        p.setActualStartDate(req.getActualStartDate());
        p.setActualEndDate(req.getActualEndDate());
        p.setPlanWorkdays(req.getPlanWorkdays());
        p.setBudgetEstimate(req.getBudgetEstimate());

        // 校验 BU/PL/RP 一致性(若都填了)
        validateBuPlRpChain(p.getBuId(), p.getPlId(), p.getRelatedProductId());

        Project saved = projectRepository.save(p);

        // ===== V2.3 项目组成员 — 一次性写入(可选) =====
        if (req.getMembers() != null && !req.getMembers().isEmpty()) {
            memberService.addBatch(saved.getId(), req.getMembers());
        }

        return getDetail(saved.getId());
    }

    private void validateBuPlRpChain(Long buId, Long plId, Long rpId) {
        if (plId != null) {
            ProductLine pl = plRepo.findByIdAndDeletedFalse(plId)
                    .orElseThrow(() -> new BusinessException("PL not found: " + plId));
            if (buId != null && pl.getBu() != null && !pl.getBu().getId().equals(buId)) {
                throw new BusinessException("PL " + plId + " 不属于 BU " + buId
                        + " (实际归属 BU=" + pl.getBu().getId() + ")");
            }
        }
        if (rpId != null) {
            RelatedProduct rp = rpRepo.findByIdAndDeletedFalse(rpId)
                    .orElseThrow(() -> new BusinessException("RelatedProduct not found: " + rpId));
            if (plId != null && rp.getPl() != null && !rp.getPl().getId().equals(plId)) {
                throw new BusinessException("RelatedProduct " + rpId + " 不属于 PL " + plId
                        + " (实际归属 PL=" + rp.getPl().getId() + ")");
            }
        }
    }

    // ===== 旧版 update(用 Project 实体)— 保留向后兼容 =====
    @Transactional
    public Project update(Long id, Project patch) {
        Project p = get(id);
        if (patch.getName() != null) p.setName(patch.getName());
        if (patch.getCustomer() != null) p.setCustomer(patch.getCustomer());
        if (patch.getDescription() != null) p.setDescription(patch.getDescription());
        if (patch.getBackground() != null) p.setBackground(patch.getBackground());
        if (patch.getGoals() != null) p.setGoals(patch.getGoals());
        if (patch.getScope() != null) p.setScope(patch.getScope());
        if (patch.getPlanStartDate() != null) p.setPlanStartDate(patch.getPlanStartDate());
        if (patch.getPlanEndDate() != null) p.setPlanEndDate(patch.getPlanEndDate());
        if (patch.getActualStartDate() != null) p.setActualStartDate(patch.getActualStartDate());
        if (patch.getActualEndDate() != null) p.setActualEndDate(patch.getActualEndDate());
        if (patch.getPlanWorkdays() != null) p.setPlanWorkdays(patch.getPlanWorkdays());
        if (patch.getBudgetEstimate() != null) p.setBudgetEstimate(patch.getBudgetEstimate());
        if (patch.getHealth() != null && patch.getHealth().getId() != null) {
            p.setHealth(healthRepo.findById(patch.getHealth().getId()).orElse(p.getHealth()));
        }
        return p;
    }

    // ===== 新版:用 DTO 接收,code 字符串 → entity 转换 =====
    @Transactional
    public ProjectDetailResponse updateFromRequest(Long id, ProjectUpdateRequest patch) {
        Project p = get(id);
        if (patch.getName() != null) p.setName(patch.getName());
        if (patch.getCustomer() != null) p.setCustomer(patch.getCustomer());
        if (patch.getDescription() != null) p.setDescription(patch.getDescription());
        if (patch.getBackground() != null) p.setBackground(patch.getBackground());
        if (patch.getGoals() != null) p.setGoals(patch.getGoals());
        if (patch.getScope() != null) p.setScope(patch.getScope());
        if (patch.getPlanStartDate() != null) p.setPlanStartDate(patch.getPlanStartDate());
        if (patch.getPlanEndDate() != null) p.setPlanEndDate(patch.getPlanEndDate());
        if (patch.getActualStartDate() != null) p.setActualStartDate(patch.getActualStartDate());
        if (patch.getActualEndDate() != null) p.setActualEndDate(patch.getActualEndDate());
        if (patch.getPlanWorkdays() != null) p.setPlanWorkdays(patch.getPlanWorkdays());
        if (patch.getBudgetEstimate() != null) p.setBudgetEstimate(patch.getBudgetEstimate());
        if (patch.getPmUserId() != null) p.setPmUserId(patch.getPmUserId());
        if (patch.getBuId() != null)             p.setBuId(patch.getBuId());
        if (patch.getPlId() != null)             p.setPlId(patch.getPlId());
        if (patch.getRelatedProductId() != null) p.setRelatedProductId(patch.getRelatedProductId());

        if (patch.getHealthCode() != null && !patch.getHealthCode().isBlank()) {
            HealthLevel h = healthRepo.findByCode(patch.getHealthCode())
                    .orElseThrow(() -> new BusinessException("Unknown healthCode: " + patch.getHealthCode()));
            p.setHealth(h);
        }
        validateBuPlRpChain(p.getBuId(), p.getPlId(), p.getRelatedProductId());
        return getDetail(id);
    }

    @Transactional
    public void softDelete(Long id) {
        Project p = get(id);
        p.setDeleted(true);
    }
}
