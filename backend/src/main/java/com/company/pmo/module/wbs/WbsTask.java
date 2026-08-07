package com.company.pmo.module.wbs;

import com.company.pmo.common.entity.SoftDeletableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * WBS 任务节点(工作分解结构的最基本单元)。
 * <p>对齐 V2.5 {@code wbs_task} 表。
 * 自引用 parent_id, 顶层 parentId = null。
 * <p>关键字段:
 * <ul>
 *   <li>{@code wbsCode} — 树内唯一编码 (e.g. "1.1.2")</li>
 *   <li>{@code weight} — 加权进度系数 (1-10)</li>
 *   <li>{@code progressPct} — 该任务自身进度 0-100</li>
 *   <li>{@code predecessorIdsJson} — 紧前任务 id 列表,MySQL JSON 列 + 文本双向兼容</li>
 * </ul>
 */
@Entity
@Table(name = "wbs_task", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wbs_task_project_code", columnNames = {"project_id", "wbs_code"})
})
@Getter @Setter @NoArgsConstructor
public class WbsTask extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "wbs_code", nullable = false, length = 32)
    private String wbsCode;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(name = "task_type", nullable = false, length = 16)
    private String taskType = "EXECUTION";   // SUMMARY / EXECUTION / MILESTONE / DELIVERABLE

    @Column(nullable = false, length = 16)
    private String status = "NOT_STARTED";   // NOT_STARTED / IN_PROGRESS / COMPLETED / BLOCKED / CANCELLED

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "plan_start_date") private LocalDate planStartDate;
    @Column(name = "plan_end_date")   private LocalDate planEndDate;
    @Column(name = "actual_start_date") private LocalDate actualStartDate;
    @Column(name = "actual_end_date")   private LocalDate actualEndDate;

    @Column(name = "plan_hours",   nullable = false, precision = 10, scale = 2)
    private BigDecimal planHours = BigDecimal.ZERO;

    @Column(name = "actual_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal actualHours = BigDecimal.ZERO;

    @Column(name = "progress_pct", nullable = false)
    private Integer progressPct = 0;        // 0-100 (DB 是 integer)

    @Column(nullable = false)
    private Integer weight = 1;             // 1-10 (DB 是 integer)

    @Column(name = "is_critical",  nullable = false) private boolean critical  = false;
    @Column(name = "is_milestone", nullable = false) private boolean milestone = false;

    @Column(name = "milestone_id") private Long milestoneId;

    /**
     * 紧前任务 id 列表 (MySQL 走 JSON, 写入时由应用层序列化为 "[1,5,12]")。
     * <p>注意:Long[] 默认会成 binary, MySQL JSON 列拒绝;改用 String 存 JSON 文本。</p>
     */
    @Column(name = "predecessor_ids", columnDefinition = "json")
    private String predecessorIdsJson = "[]";

    @Column(columnDefinition = "text") private String deliverable;
    @Column(columnDefinition = "text") private String remark;

    @Column(name = "created_by") private Long createdBy;

    // 临时字段(不持久化): 拼装树时由 Mapper 填充
    @Transient private List<WbsTask> children = new ArrayList<>();

    /** 兼容旧 API: 返回 Long[] (从 JSON 反序列化) */
    @Transient
    @JsonIgnore
    public Long[] getPredecessorIds() {
        if (predecessorIdsJson == null || predecessorIdsJson.isBlank()) return new Long[0];
        try {
            ObjectMapper m = new ObjectMapper();
            return m.readValue(predecessorIdsJson, Long[].class);
        } catch (Exception e) {
            return new Long[0];
        }
    }

    public void setPredecessorIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            this.predecessorIdsJson = "[]";
        } else {
            try {
                ObjectMapper m = new ObjectMapper();
                this.predecessorIdsJson = m.writeValueAsString(ids);
            } catch (Exception e) {
                this.predecessorIdsJson = "[]";
            }
        }
    }

    /** 方便前端 / 序列化, 转 List<Long> */
    public List<Long> predecessorIdList() {
        Long[] arr = getPredecessorIds();
        return arr == null ? List.of() : List.of(arr);
    }
}
