package com.company.zhiyu.module.menu;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 角色 × 菜单 关联 (L1-3)
 * 联合主键: (role_id, menu_id)
 */
@Entity
@Table(name = "role_menu")
@IdClass(RoleMenu.PK.class)
@Getter @Setter @NoArgsConstructor
public class RoleMenu {

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Id
    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "granted_by")
    private Long grantedBy;

    /** 联合主键 */
    @NoArgsConstructor
    @Getter @Setter
    public static class PK implements Serializable {
        private Long roleId;
        private Long menuId;

        public PK(Long roleId, Long menuId) { this.roleId = roleId; this.menuId = menuId; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(roleId, pk.roleId) && Objects.equals(menuId, pk.menuId);
        }
        @Override public int hashCode() { return Objects.hash(roleId, menuId); }
    }
}