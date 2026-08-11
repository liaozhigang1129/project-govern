package com.hex.projectgovern.module.initiation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * @deprecated 改读 {@link com.hex.projectgovern.module.approval.ApprovalFlowActionRepository}
 * @see ApprovalRecord
 */
@Deprecated
public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {
    /**
     * @deprecated 改读 ApprovalFlowAction (WP-M7-05)
     */
    @Deprecated
    List<ApprovalRecord> findByInitiationIdOrderByDecidedAtAsc(Long initiationId);
}
