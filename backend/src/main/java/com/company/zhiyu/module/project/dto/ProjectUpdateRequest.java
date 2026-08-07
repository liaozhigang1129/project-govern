package com.company.zhiyu.module.project.dto;

/**
 * 局部更新 — 所有字段都可选(code 不可改,业务约定)
 */
public class ProjectUpdateRequest {

    private String name;
    private String healthCode;        // 字典 code
    private Long buId;                // 业务单元
    private Long plId;                // 产品线
    private Long relatedProductId;    // 关联产品
    private String customer;
    private String description;
    private String background;
    private String goals;
    private String scope;
    private java.time.LocalDate planStartDate;
    private java.time.LocalDate planEndDate;
    private java.time.LocalDate actualStartDate;
    private java.time.LocalDate actualEndDate;
    private Integer planWorkdays;
    private java.math.BigDecimal budgetEstimate;
    private Long pmUserId;            // 项目经理可改(转交)

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
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
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }
    public String getGoals() { return goals; }
    public void setGoals(String goals) { this.goals = goals; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public java.time.LocalDate getPlanStartDate() { return planStartDate; }
    public void setPlanStartDate(java.time.LocalDate planStartDate) { this.planStartDate = planStartDate; }
    public java.time.LocalDate getPlanEndDate() { return planEndDate; }
    public void setPlanEndDate(java.time.LocalDate planEndDate) { this.planEndDate = planEndDate; }
    public java.time.LocalDate getActualStartDate() { return actualStartDate; }
    public void setActualStartDate(java.time.LocalDate actualStartDate) { this.actualStartDate = actualStartDate; }
    public java.time.LocalDate getActualEndDate() { return actualEndDate; }
    public void setActualEndDate(java.time.LocalDate actualEndDate) { this.actualEndDate = actualEndDate; }
    public Integer getPlanWorkdays() { return planWorkdays; }
    public void setPlanWorkdays(Integer planWorkdays) { this.planWorkdays = planWorkdays; }
    public java.math.BigDecimal getBudgetEstimate() { return budgetEstimate; }
    public void setBudgetEstimate(java.math.BigDecimal budgetEstimate) { this.budgetEstimate = budgetEstimate; }
    public Long getPmUserId() { return pmUserId; }
    public void setPmUserId(Long pmUserId) { this.pmUserId = pmUserId; }
}
