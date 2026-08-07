package com.hex.projectgovern.module.milestoneai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MilestoneAiSignalRepository extends JpaRepository<MilestoneAiSignal, Long> {
    List<MilestoneAiSignal> findByAdvisoryIdOrderByIdAsc(Long advisoryId);
    void deleteByAdvisoryId(Long advisoryId);
}
