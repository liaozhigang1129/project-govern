package com.company.zhiyu.module.dingtalk;

import com.company.zhiyu.module.org.Department;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DingTalkDepartmentLookupRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * 同步专用: 不带 deleted 过滤, 让 upsert 流程可以"复活"软删记录 (钉钉 dept 重新出现时复用原 code/dingtalk_dept_id)。
     * 表上 uq_department_dingtalk 是 partial unique (WHERE deleted=0), 所以复活时不会撞键。
     * department.code 的整列 UNIQUE 仍可能撞旧软删记录, 见 V4.33 Flyway 脚本。
     */
    public Optional<Department> findByDingtalkDeptIdIncludingDeleted(Long dingtalkDeptId) {
        List<Department> list = em.createQuery(
                "SELECT d FROM Department d WHERE d.dingtalkDeptId = :did ORDER BY d.deleted ASC, d.id ASC",
                Department.class)
                .setParameter("did", dingtalkDeptId)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Department> findByDingtalkDeptId(Long dingtalkDeptId) {
        List<Department> list = em.createQuery(
                "SELECT d FROM Department d WHERE d.dingtalkDeptId = :did AND d.deleted = false",
                Department.class)
                .setParameter("did", dingtalkDeptId)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Department> findByCode(String code) {
        List<Department> list = em.createQuery(
                "SELECT d FROM Department d WHERE d.code = :code AND d.deleted = false",
                Department.class)
                .setParameter("code", code)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
