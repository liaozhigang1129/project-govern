package com.hex.projectgovern.module.project.dto;

import java.time.LocalDate;

/**
 * 项目列表查询参数
 *  - 字段全部可选,null 视为不过滤
 *  - 命名与前端的 form 字段对齐
 */
public class ProjectQuery {

    /** 业务单元 id */
    private Long buId;
    /** 产品线 id */
    private Long plId;
    /** 项目经理 id */
    private Long pmUserId;
    /** 计划开始(>=) */
    private LocalDate planStartFrom;
    /** 计划开始(<=) */
    private LocalDate planStartTo;
    /** 关键字(模糊匹配 name/code) */
    private String keyword;

    public Long getBuId() { return buId; }
    public void setBuId(Long buId) { this.buId = buId; }
    public Long getPlId() { return plId; }
    public void setPlId(Long plId) { this.plId = plId; }
    public Long getPmUserId() { return pmUserId; }
    public void setPmUserId(Long pmUserId) { this.pmUserId = pmUserId; }
    public LocalDate getPlanStartFrom() { return planStartFrom; }
    public void setPlanStartFrom(LocalDate planStartFrom) { this.planStartFrom = planStartFrom; }
    public LocalDate getPlanStartTo() { return planStartTo; }
    public void setPlanStartTo(LocalDate planStartTo) { this.planStartTo = planStartTo; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
