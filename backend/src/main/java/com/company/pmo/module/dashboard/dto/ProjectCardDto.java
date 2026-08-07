package com.company.pmo.module.dashboard.dto;

import com.company.pmo.module.project.Project;
import com.company.pmo.module.project.dto.ProjectDetailResponse.DictRef;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 项目卡片(列表专用) — 嵌套字典,跟 ProjectDetailResponse 形状保持一致
 * <p>前端 ProjectCard 类型只有一个,任何 /projects 接口返回的数组里
 * row.type / row.status / row.health 都是 DictRef,代码可复用。
 *
 * <p>P2-扩展:支持 BU/PL/关联产品 + 项目经理姓名 嵌套展示
 */
public record ProjectCardDto(
        Long id,
        String code,
        String name,
        String customer,
        DictRef type,
        DictRef status,
        DictRef health,
        DictRef bu,
        DictRef pl,
        DictRef relatedProduct,
        Long pmUserId,
        String pmUserName,
        LocalDate planStartDate,
        LocalDate planEndDate,
        Integer progressPct,
        java.math.BigDecimal budgetEstimate,
        Instant updatedAt
) {
    public static ProjectCardDto from(Project p) {
        return new ProjectCardDto(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getCustomer(),
                p.getType()   != null ? new DictRef(p.getType().getId(),   p.getType().getCode(),   p.getType().getName()) : null,
                p.getStatus() != null ? new DictRef(p.getStatus().getId(), p.getStatus().getCode(), p.getStatus().getName()) : null,
                p.getHealth() != null ? new DictRef(p.getHealth().getId(), p.getHealth().getCode(), p.getHealth().getName(), p.getHealth().getColorHex()) : null,
                null,   // bu — 由 service 层注入
                null,   // pl
                null,   // relatedProduct
                p.getPmUserId(),
                null,   // pmUserName — 由 service 层注入
                p.getPlanStartDate(),
                p.getPlanEndDate(),
                p.getProgressPct(),
                p.getBudgetEstimate(),
                p.getUpdatedAt()
        );
    }
}
