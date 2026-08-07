package com.company.zhiyu.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeletableEntity extends AuditableEntity {
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;
}
