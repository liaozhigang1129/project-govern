package com.hex.projectgovern.module.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalFlowInstanceRepository extends JpaRepository<ApprovalFlowInstance, Long> {

    Optional<ApprovalFlowInstance> findByKindAndBizId(String kind, Long bizId);

    List<ApprovalFlowInstance> findByApplicantIdAndStatusIn(Long applicantId, List<ApprovalStatus> statuses);

    List<ApprovalFlowInstance> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);
}