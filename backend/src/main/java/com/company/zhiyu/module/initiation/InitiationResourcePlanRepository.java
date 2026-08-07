package com.company.zhiyu.module.initiation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InitiationResourcePlanRepository extends JpaRepository<InitiationResourcePlan, Long> {

    List<InitiationResourcePlan> findByInitiationIdAndDeletedFalseOrderByIdAsc(Long initiationId);
}
