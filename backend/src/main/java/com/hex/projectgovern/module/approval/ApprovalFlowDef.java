package com.hex.projectgovern.module.approval;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 审批流程定义 (DB 配置驱动)
 * 一个 kind (initiation/timesheet/risk/budget) 可有多个 version (灰度升级)
 */
@Entity
@Table(name = "approval_flow_def",
       uniqueConstraints = @UniqueConstraint(columnNames = {"kind", "code", "version"}))
@Getter @Setter @NoArgsConstructor
public class ApprovalFlowDef {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(nullable = false, length = 32) private String kind;

    @Column(nullable = false, length = 64) private String code;

    @Column(nullable = false, length = 128) private String name;

    @Column(nullable = false) private Integer version = 1;

    @Column(nullable = false) private Boolean enabled = true;

    @Column(columnDefinition = "text") private String description;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    @PreUpdate
    void touchUpdatedAt() { this.updatedAt = Instant.now(); }
}