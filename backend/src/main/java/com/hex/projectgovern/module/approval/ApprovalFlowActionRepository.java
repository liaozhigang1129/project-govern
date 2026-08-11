package com.hex.projectgovern.module.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalFlowActionRepository extends JpaRepository<ApprovalFlowAction, Long> {

    List<ApprovalFlowAction> findByInstanceIdOrderByDecidedAtAsc(Long instanceId);
}