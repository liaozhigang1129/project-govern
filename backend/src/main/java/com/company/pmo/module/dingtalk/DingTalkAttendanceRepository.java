package com.company.pmo.module.dingtalk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DingTalkAttendanceRepository
        extends JpaRepository<DingTalkAttendance, Long>, JpaSpecificationExecutor<DingTalkAttendance> {

    Optional<DingTalkAttendance> findByRecordIdAndDeletedFalse(String recordId);

    @Query("SELECT a FROM DingTalkAttendance a WHERE a.userid = ?1 AND a.deleted = false ORDER BY a.workDate DESC, a.checkType ASC")
    List<DingTalkAttendance> findByUseridAndDeletedFalse(String userid);

    @Query("SELECT a FROM DingTalkAttendance a WHERE a.deleted = false ORDER BY a.workDate DESC, a.checkType ASC")
    Page<DingTalkAttendance> findAllActive(Pageable pageable);

    @Query("SELECT COUNT(a) FROM DingTalkAttendance a WHERE a.deleted = false")
    long countActive();

    @Query("SELECT COUNT(a) FROM DingTalkAttendance a WHERE a.deleted = false AND a.workDate BETWEEN ?1 AND ?2")
    long countByDateRange(LocalDate from, LocalDate to);

    /**
     * V4.33 详情抽屉用: 按 record_id IN (...) 查老表原始打卡
     * 老表冻结只读, 不写, 仅读
     */
    @Query("SELECT a FROM DingTalkAttendance a WHERE a.recordId IN ?1 AND a.deleted = false ORDER BY a.workDate DESC, a.actualTime ASC")
    List<DingTalkAttendance> findAllByRecordIdIn(List<String> recordIds);
}
