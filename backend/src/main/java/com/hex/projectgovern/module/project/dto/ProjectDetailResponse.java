package com.hex.projectgovern.module.project.dto;

import com.hex.projectgovern.module.dict.HealthLevel;
import com.hex.projectgovern.module.dict.ProjectStatus;
import com.hex.projectgovern.module.dict.ProjectType;
import com.hex.projectgovern.module.project.Project;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 项目详情 — DTO 形式,避免反序列化 LAZY proxy
 * <p>同时把字典 id 还原为 code+name,前端友好。
 *
 * <p>P2-扩展:包含 BU/PL/关联产品/pmUserName(由 service 层注入)
 */
public class ProjectDetailResponse {
    public Long id;
    public String code;
    public String name;
    public DictRef type;
    public DictRef status;
    public DictRef health;
    public DictRef bu;
    public DictRef pl;
    public DictRef relatedProduct;
    public String customer;
    public Long departmentId;
    public Long pmUserId;
    public String pmUserName;
    public Long sponsorUserId;
    public String description;
    public String background;
    public String goals;
    public String scope;
    public LocalDate planStartDate;
    public LocalDate planEndDate;
    public LocalDate actualStartDate;
    public LocalDate actualEndDate;
    public Integer planWorkdays;
    public int progressPct;
    public BigDecimal budgetEstimate;

    // ==================== EVM (P3 挣值分析冗余字段) ====================
    public BigDecimal bac;
    public BigDecimal evmCpi;
    public BigDecimal evmSpi;
    public BigDecimal evmEac;
    public BigDecimal evmEtc;
    public BigDecimal evmVac;
    public Instant evmUpdatedAt;
    public Integer baselineVersion;
    public Instant baselineFrozenAt;
    public Long baselineFrozenBy;

    public Instant createdAt;
    public Instant updatedAt;

    public Long buId;
    public Long plId;
    public Long relatedProductId;

    public static class DictRef {
        public Long id;
        public String code;
        public String name;
        public String colorHex;   // 仅 HealthLevel 有
        public Long parentId;     // PL.parentId=buId,RP.parentId=plId
        public String version;    // 仅 RelatedProduct 有

        public DictRef() {}
        public DictRef(Long id, String code, String name) { this.id = id; this.code = code; this.name = name; }
        public DictRef(Long id, String code, String name, String colorHex) {
            this(id, code, name); this.colorHex = colorHex;
        }
    }

    public static ProjectDetailResponse from(Project p) {
        ProjectDetailResponse d = new ProjectDetailResponse();
        d.id = p.getId();
        d.code = p.getCode();
        d.name = p.getName();

        ProjectType t = p.getType();
        if (t != null) d.type = new DictRef(t.getId(), t.getCode(), t.getName());

        ProjectStatus s = p.getStatus();
        if (s != null) d.status = new DictRef(s.getId(), s.getCode(), s.getName());

        HealthLevel h = p.getHealth();
        if (h != null) {
            d.health = new DictRef(h.getId(), h.getCode(), h.getName(), h.getColorHex());
        } else {
            d.health = new DictRef(1L, "GREEN", "正常", "#67C23A");
        }

        d.customer = p.getCustomer();
        d.departmentId = p.getDepartmentId();
        d.pmUserId = p.getPmUserId();
        d.sponsorUserId = p.getSponsorUserId();
        d.description = p.getDescription();
        d.background = p.getBackground();
        d.goals = p.getGoals();
        d.scope = p.getScope();
        d.planStartDate = p.getPlanStartDate();
        d.planEndDate = p.getPlanEndDate();
        d.actualStartDate = p.getActualStartDate();
        d.actualEndDate = p.getActualEndDate();
        d.planWorkdays = p.getPlanWorkdays();
        d.progressPct = p.getProgressPct();
        d.budgetEstimate = p.getBudgetEstimate();

        // EVM
        d.bac = p.getBac();
        d.evmCpi = p.getEvmCpi();
        d.evmSpi = p.getEvmSpi();
        d.evmEac = p.getEvmEac();
        d.evmEtc = p.getEvmEtc();
        d.evmVac = p.getEvmVac();
        d.evmUpdatedAt = p.getEvmUpdatedAt();
        d.baselineVersion = p.getBaselineVersion();
        d.baselineFrozenAt = p.getBaselineFrozenAt();
        d.baselineFrozenBy = p.getBaselineFrozenBy();

        d.createdAt = p.getCreatedAt();
        d.updatedAt = p.getUpdatedAt();

        d.buId = p.getBuId();
        d.plId = p.getPlId();
        d.relatedProductId = p.getRelatedProductId();
        return d;
    }
}
