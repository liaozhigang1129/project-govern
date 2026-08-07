package com.company.zhiyu.module.initiation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {
    List<ApprovalRecord> findByInitiationIdOrderByDecidedAtAsc(Long initiationId);
}
