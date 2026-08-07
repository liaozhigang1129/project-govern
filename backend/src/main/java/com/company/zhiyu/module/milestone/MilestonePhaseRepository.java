package com.company.zhiyu.module.milestone;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MilestonePhaseRepository extends JpaRepository<MilestonePhase, Long> {
    List<MilestonePhase> findAllByOrderBySortOrderAsc();
}
