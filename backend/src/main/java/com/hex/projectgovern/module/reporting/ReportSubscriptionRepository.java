package com.hex.projectgovern.module.reporting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReportSubscriptionRepository extends JpaRepository<ReportSubscription, Long> {
    List<ReportSubscription> findByUserId(Long userId);
    java.util.Optional<ReportSubscription> findByCode(String code);
    @Query("SELECT s FROM ReportSubscription s WHERE s.status = 'ACTIVE' AND s.nextRunAt < :now")
    List<ReportSubscription> findDueSubscriptions(@Param("now") Instant now);
}
