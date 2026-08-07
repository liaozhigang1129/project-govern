package com.hex.projectgovern.module.resourcepipeline;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ResourceSkillRepository extends JpaRepository<ResourceSkill, Long> {

    @Query(value = "SELECT COUNT(DISTINCT user_id) FROM resource_skill WHERE deleted = false",
        nativeQuery = true)
    long countDistinctUsers();

    /**
     * PG: certified 列是 SMALLINT (V4.14 SQL 建表),entity 也是 Byte
     * PG 不支持 smallint = boolean,改用 = 1
     */
    @Query(value = """
        SELECT skill_code, COUNT(*), AVG(skill_level),
               SUM(CASE WHEN certified = 1 THEN 1 ELSE 0 END) AS cert_count
        FROM resource_skill
        WHERE deleted = false
        GROUP BY skill_code
        ORDER BY COUNT(*) DESC
        LIMIT 20
    """, nativeQuery = true)
    List<Object[]> aggregateSkillStats();
}