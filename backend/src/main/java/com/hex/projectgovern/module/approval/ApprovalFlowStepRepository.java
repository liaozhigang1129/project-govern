package com.hex.projectgovern.module.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalFlowStepRepository extends JpaRepository<ApprovalFlowStep, Long> {

    List<ApprovalFlowStep> findByFlowDefIdOrderByStepNoAsc(Long flowDefId);
}