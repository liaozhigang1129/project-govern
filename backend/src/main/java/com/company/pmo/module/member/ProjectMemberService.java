package com.company.pmo.module.member;

import com.company.pmo.common.exception.BusinessException;
import com.company.pmo.module.member.dto.*;
import com.company.pmo.module.org.AppUser;
import com.company.pmo.module.org.UserRepository;
import com.company.pmo.module.project.Project;
import com.company.pmo.module.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目成员 — 业务逻辑
 *
 * <p>职责:
 * <ul>
 *   <li>角色字典查询(给前端下拉用)</li>
 *   <li>成员的增/改/删/查</li>
 *   <li>业务校验:角色必须存在、user_id 必须存在、日期范围、投入比例、成员姓名</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository memberRepo;
    private final MemberRoleRepository roleRepo;
    private final ProjectRepository projectRepo;
    private final UserRepository userRepo;

    // ===== 角色字典 =====

    /** 拉所有启用的角色(下拉用) */
    @Transactional(readOnly = true)
    public List<MemberRoleResponse> listRoles() {
        return MemberRoleResponse.fromList(roleRepo.findActive());
    }

    // ===== 成员 CRUD =====

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listByProject(Long projectId) {
        ensureProjectExists(projectId);
        return memberRepo.findActiveByProject(projectId)
                .stream().map(ProjectMemberResponse::from).toList();
    }

    @Transactional
    public ProjectMemberResponse add(Long projectId, ProjectMemberRequest req) {
        ensureProjectExists(projectId);
        ProjectMember m = buildMember(projectId, req);
        return ProjectMemberResponse.from(memberRepo.save(m));
    }

    @Transactional
    public ProjectMemberResponse update(Long memberId, ProjectMemberRequest req) {
        ProjectMember m = memberRepo.findByIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new BusinessException("Member not found: " + memberId));
        applyRequest(m, req);
        return ProjectMemberResponse.from(memberRepo.save(m));
    }

    @Transactional
    public void delete(Long memberId) {
        ProjectMember m = memberRepo.findByIdAndDeletedFalse(memberId)
                .orElseThrow(() -> new BusinessException("Member not found: " + memberId));
        m.setDeleted(true);
        memberRepo.save(m);
    }

    // ===== 批量(供 ProjectService.create 调用) =====

    /**
     * 给项目批量添加成员(项目新建时一次性写入)
     * <p>不抛业务异常时静默失败一条不影响其他(返回成功条数给调用方)</p>
     */
    @Transactional
    public int addBatch(Long projectId, List<ProjectMemberRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) return 0;
        ensureProjectExists(projectId);
        int ok = 0;
        for (ProjectMemberRequest r : reqs) {
            try {
                memberRepo.save(buildMember(projectId, r));
                ok++;
            } catch (BusinessException be) {
                // 单条失败不阻塞整批
            }
        }
        return ok;
    }

    // ===== 私有工具 =====

    private void ensureProjectExists(Long projectId) {
        projectRepo.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> new BusinessException("Project not found: " + projectId));
    }

    private ProjectMember buildMember(Long projectId, ProjectMemberRequest req) {
        ProjectMember m = new ProjectMember();
        m.setProjectId(projectId);
        applyRequest(m, req);
        return m;
    }

    private void applyRequest(ProjectMember m, ProjectMemberRequest req) {
        // 1) 角色
        MemberRole role = roleRepo.findByCode(req.getRoleCode())
                .orElseThrow(() -> new BusinessException("Unknown roleCode: " + req.getRoleCode()));
        m.setRole(role);

        // 2) 用户
        m.setUserId(req.getUserId());
        m.setExternal(req.isExternal());

        if (req.getUserId() != null) {
            // 内部 user: 校验存在 + 自动取 fullName
            AppUser u = userRepo.findById(req.getUserId())
                    .orElseThrow(() -> new BusinessException("Unknown userId: " + req.getUserId()));
            m.setMemberName(u.getFullName());
        } else {
            // 外部人员: 必填姓名
            if (!req.isExternal()) {
                throw new BusinessException("未指定 userId 时,必须勾选「外部人员」并填写姓名");
            }
            if (req.getMemberName() == null || req.getMemberName().isBlank()) {
                throw new BusinessException("外部人员必须填写 memberName");
            }
            m.setMemberName(req.getMemberName().trim());
        }

        // 3) 日期
        m.setJoinDate(req.getJoinDate());
        m.setLeaveDate(req.getLeaveDate());
        if (req.getLeaveDate() != null && req.getLeaveDate().isBefore(req.getJoinDate())) {
            throw new BusinessException("参与结束日期不能早于开始日期");
        }

        // 4) 投入比例
        m.setAllocationPct(req.getAllocationPct());

        // 5) 备注
        m.setRemark(req.getRemark());
    }
}
