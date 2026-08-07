package com.hex.projectgovern.module.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MemberRoleRepository extends JpaRepository<MemberRole, Long> {

    /**
     * 拉所有启用的角色(下拉用)
     * <p>显式 JPQL(不依赖方法名推导),便于后续加缓存/排序</p>
     * <p>注意:MemberRole 自身不软删(角色字典是配置项,不走 deleted 字段),只按 enabled 过滤</p>
     */
    @Query("SELECT r FROM MemberRole r WHERE r.enabled = true AND r.deleted = false ORDER BY r.sortOrder ASC, r.id ASC")
    List<MemberRole> findActive();

    /**
     * 按 code 查(软删过滤)— 业务校验用
     */
    @Query("SELECT r FROM MemberRole r WHERE r.code = :code AND r.deleted = false")
    Optional<MemberRole> findByCode(@org.springframework.data.repository.query.Param("code") String code);
}
