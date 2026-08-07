package com.hex.projectgovern.module.risk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskBucketRepository extends JpaRepository<RiskBucket, Long> {

    Optional<RiskBucket> findByCode(String code);

    /**
     * V4.31 软删除列补齐: cache 加载只取未删除的桶.
     * 旧方法 {@link #findAllByEnabledTrueOrderBySortOrderAsc()} 保留 (给旧调用方/测试用).
     */
    List<RiskBucket> findAllByEnabledTrueAndDeletedFalseOrderBySortOrderAsc();

    /** 兼容旧调用: 仅过滤 enabled, 不管 deleted (一般内部专用) */
    List<RiskBucket> findAllByEnabledTrueOrderBySortOrderAsc();

    boolean existsByCode(String code);
}
