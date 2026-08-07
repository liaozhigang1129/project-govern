package com.company.pmo.module.member.dto;

import com.company.pmo.module.member.ProjectMember;

import java.time.LocalDate;

/**
 * 项目成员响应 DTO
 * 包含 role 字典(code+name),便于前端直接显示,无需二次查字典
 */
public class ProjectMemberResponse {

    public Long id;
    public Long projectId;
    public Long userId;
    public String memberName;
    public boolean external;
    public LocalDate joinDate;
    public LocalDate leaveDate;
    public int allocationPct;
    public String remark;

    public RoleRef role;

    public static class RoleRef {
        public Long id;
        public String code;
        public String name;

        public RoleRef() {}
        public RoleRef(Long id, String code, String name) {
            this.id = id; this.code = code; this.name = name;
        }
    }

    public static ProjectMemberResponse from(ProjectMember m) {
        ProjectMemberResponse d = new ProjectMemberResponse();
        d.id = m.getId();
        d.projectId = m.getProjectId();
        d.userId = m.getUserId();
        d.memberName = m.getMemberName();
        d.external = m.isExternal();
        d.joinDate = m.getJoinDate();
        d.leaveDate = m.getLeaveDate();
        d.allocationPct = m.getAllocationPct();
        d.remark = m.getRemark();
        if (m.getRole() != null) {
            d.role = new RoleRef(m.getRole().getId(), m.getRole().getCode(), m.getRole().getName());
        }
        return d;
    }
}
