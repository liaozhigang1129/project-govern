package com.company.zhiyu.module.org;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 用户-角色多对多关联 (L1-1 用户管理)
 * - 兼容老的 primary_role_id (1 个角色)
 * - 这里允许 1 个用户有 N 个角色 (用于"主角色 + 兼任"场景)
 * - 复合主键: (user_id, role_id)
 */
@Entity
@Table(name = "user_role_assignments")
@IdClass(UserRole.UserRoleId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserRole {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "granted_by")
    private Long grantedBy;

    /** 复合主键类 */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UserRoleId implements Serializable {
        private Long userId;
        private Long roleId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserRoleId that)) return false;
            return Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, roleId);
        }
    }
}
