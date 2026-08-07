package com.company.pmo.module.dingtalk;

import com.company.pmo.module.org.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DingTalkRoleLookupRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<Role> findByCode(String code) {
        List<Role> list = em.createQuery(
                "SELECT r FROM Role r WHERE r.code = :code",
                Role.class)
                .setParameter("code", code)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 字典序最小的角色 — 同步新用户的 fallback 默认角色 */
    public Optional<Role> findDefaultRole() {
        List<Role> list = em.createQuery(
                "SELECT r FROM Role r ORDER BY r.code ASC",
                Role.class)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
