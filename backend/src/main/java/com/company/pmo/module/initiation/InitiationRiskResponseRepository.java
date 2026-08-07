package com.company.pmo.module.initiation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InitiationRiskResponseRepository extends JpaRepository<InitiationRiskResponse, Long> {

    List<InitiationRiskResponse> findByInitiationIdAndDeletedFalseOrderByIdAsc(Long initiationId);
}
