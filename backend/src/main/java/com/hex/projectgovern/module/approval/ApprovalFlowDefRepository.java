package com.hex.projectgovern.module.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalFlowDefRepository extends JpaRepository<ApprovalFlowDef, Long> {

    List<ApprovalFlowDef> findByKindAndEnabledTrueOrderByVersionDesc(String kind);

    default Optional<ApprovalFlowDef> findLatestEnabled(String kind) {
        return findByKindAndEnabledTrueOrderByVersionDesc(kind).stream().findFirst();
    }
}