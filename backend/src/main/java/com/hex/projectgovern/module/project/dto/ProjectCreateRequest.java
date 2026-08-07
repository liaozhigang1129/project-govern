package com.hex.projectgovern.module.project.dto;

import com.hex.projectgovern.module.member.dto.ProjectMemberRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新建项目请求 DTO
 *
 * <p>前端契约(2025-Q3 起):
 * <ul>
 *   <li>typeCode / statusCode / healthCode 用字典 code 字符串,后端转换为 id</li>
 *   <li>不允许直接传 type.id / status.id(防越权)</li>
 *   <li>BU/PL/关联产品 用 id(后台已 CRUD 出来,前端直接从字典接口拉)</li>
 *   <li>members 数组可选 — 项目组内成员(项目经理/助理/开发/测试/BA/架构/配置)一次性写入</li>
 * </ul>
 */
public class ProjectCreateRequest {

    @NotBlank @Size(max = 32)
    private String code;

    @NotBlank @Size(max = 128)
    private String name;

    @NotBlank
    private String typeCode;      // 字典:ProjectType.code

    @NotBlank
    private String statusCode;    // 字典:ProjectStatus.code

    private String healthCode;    // 字典:HealthLevel.code (可选)

    private Long buId;            // 业务单元 id(可选)
    private Long plId;            // 产品线 id(可选)
    private Long relatedProductId;// 关联产品 id(可选)

    private String customer;
    private Long departmentId;
    private Long pmUserId;
    private Long sponsorUserId;
    private String description;
    private String background;
    private String goals;
    private String scope;
    private LocalDate planStartDate;
    private LocalDate planEndDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private Integer planWorkdays;
    private BigDecimal budgetEstimate;

    /**
     * 项目组成员(可选)
     * <p>前端在「项目组成员」Tab 填写后,提交时随项目一起创建</p>
     * <p>单条失败不阻塞整批(后端 service 层吞掉 BusinessException)</p>
     */
    @Valid
    private List<ProjectMemberRequest> members;

    // --- getters / setters ---
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getHealthCode() { return healthCode; }
    public void setHealthCode(String healthCode) { this.healthCode = healthCode; }
    public Long getBuId() { return buId; }
    public void setBuId(Long buId) { this.buId = buId; }
    public Long getPlId() { return plId; }
    public void setPlId(Long plId) { this.plId = plId; }
    public Long getRelatedProductId() { return relatedProductId; }
    public void setRelatedProductId(Long relatedProductId) { this.relatedProductId = relatedProductId; }
    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getPmUserId() { return pmUserId; }
    public void setPmUserId(Long pmUserId) { this.pmUserId = pmUserId; }
    public Long getSponsorUserId() { return sponsorUserId; }
    public void setSponsorUserId(Long sponsorUserId) { this.sponsorUserId = sponsorUserId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }
    public String getGoals() { return goals; }
    public void setGoals(String goals) { this.goals = goals; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public LocalDate getPlanStartDate() { return planStartDate; }
    public void setPlanStartDate(LocalDate planStartDate) { this.planStartDate = planStartDate; }
    public LocalDate getPlanEndDate() { return planEndDate; }
    public void setPlanEndDate(LocalDate planEndDate) { this.planEndDate = planEndDate; }
    public LocalDate getActualStartDate() { return actualStartDate; }
    public void setActualStartDate(LocalDate actualStartDate) { this.actualStartDate = actualStartDate; }
    public LocalDate getActualEndDate() { return actualEndDate; }
    public void setActualEndDate(LocalDate actualEndDate) { this.actualEndDate = actualEndDate; }
    public Integer getPlanWorkdays() { return planWorkdays; }
    public void setPlanWorkdays(Integer planWorkdays) { this.planWorkdays = planWorkdays; }
    public BigDecimal getBudgetEstimate() { return budgetEstimate; }
    public void setBudgetEstimate(BigDecimal budgetEstimate) { this.budgetEstimate = budgetEstimate; }
    public List<ProjectMemberRequest> getMembers() { return members; }
    public void setMembers(List<ProjectMemberRequest> members) { this.members = members; }
}
