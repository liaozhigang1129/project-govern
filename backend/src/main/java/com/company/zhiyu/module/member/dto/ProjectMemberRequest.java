package com.company.zhiyu.module.member.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * 新增/编辑项目成员 请求 DTO
 * <p>roleCode 走字典字符串(code 形式);userId 可空(外部成员);</p>
 * <p>内部成员(memberName 留空) → 后端从 app_user.fullName 自动填入</p>
 * <p>外部成员(isExternal=true) → memberName 必填</p>
 */
public class ProjectMemberRequest {

    @NotBlank
    private String roleCode;        // PM/ASSISTANT/ARCH/BA/DEV/QA/CFG

    private Long userId;             // 内部 user id(可空)

    @Size(max = 64)
    private String memberName;       // 外部人员姓名 / 内部 user 时可空(后端填)

    private boolean external = false;

    @NotNull
    private LocalDate joinDate;      // 参与开始

    private LocalDate leaveDate;     // 参与结束(可空=仍在项目中)

    @Min(0) @Max(100)
    private int allocationPct = 100; // 投入比例

    @Size(max = 256)
    private String remark;

    // getters / setters
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public boolean isExternal() { return external; }
    public void setExternal(boolean external) { this.external = external; }
    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }
    public LocalDate getLeaveDate() { return leaveDate; }
    public void setLeaveDate(LocalDate leaveDate) { this.leaveDate = leaveDate; }
    public int getAllocationPct() { return allocationPct; }
    public void setAllocationPct(int allocationPct) { this.allocationPct = allocationPct; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
