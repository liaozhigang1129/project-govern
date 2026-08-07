package com.company.zhiyu.module.risk.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * 创建/更新风险请求 (P4 风险管理)。
 * <p>id 缺失 = 新建, 传入 = 更新。
 * <p>score / level 由 probability × impact 自动推导, 不接受客户端传入。
 *
 * @param id            主键 (新建时 null)
 * @param projectId     所属项目
 * @param code          项目内编号, e.g. R-001
 * @param title         风险标题
 * @param description   详细描述
 * @param category      TECHNICAL / SCHEDULE / COST / QUALITY / EXTERNAL / ORGANIZATIONAL / OTHER
 * @param probability   1-5
 * @param impact        1-5
 * @param status        OPEN / MITIGATING / CLOSED / OCCURRED / ACCEPTED
 * @param ownerUserId   风险责任人
 * @param mitigation    预防/缓解措施
 * @param contingency   应急/兜底措施
 * @param responseStrategy AVOID/MITIGATE/TRANSFER/ACCEPT/EXPLOIT/ENHANCE/SHARE
 * @param identifiedDate   识别日期 (默认今天)
 * @param targetCloseDate  目标关闭日期 (可选)
 * @param relatedWbsTaskId  关联 WBS 任务 (可选)
 * @param relatedMilestoneId 关联里程碑 (可选)
 */
public record RiskRequest(
        Long id,
        @NotNull Long projectId,
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 256) String title,
        String description,
        @NotBlank String category,
        @NotNull @Min(1) @Max(5) Integer probability,
        @NotNull @Min(1) @Max(5) Integer impact,
        String status,
        Long ownerUserId,
        String mitigation,
        String contingency,
        String responseStrategy,
        LocalDate identifiedDate,
        LocalDate targetCloseDate,
        Long relatedWbsTaskId,
        Long relatedMilestoneId
) {
    public String statusOrDefault() {
        return status == null || status.isBlank() ? "OPEN" : status;
    }
}
