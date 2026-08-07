package com.hex.projectgovern.module.dingtalk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DingTalkLeaveRepository extends JpaRepository<DingTalkLeave, Long>, JpaSpecificationExecutor<DingTalkLeave> {

    Optional<DingTalkLeave> findByLeaveIdAndDeletedFalse(String leaveId);

    @Query("SELECT l FROM DingTalkLeave l WHERE l.userid = ?1 AND l.deleted = false ORDER BY l.startTime DESC")
    List<DingTalkLeave> findByUseridAndDeletedFalse(String userid);

    @Query("SELECT l FROM DingTalkLeave l WHERE l.pmoUserId = ?1 AND l.deleted = false ORDER BY l.startTime DESC")
    Page<DingTalkLeave> findByPmoUserIdAndDeletedFalse(Long userId, Pageable pageable);

    @Query("SELECT l FROM DingTalkLeave l WHERE l.deleted = false ORDER BY l.startTime DESC")
    Page<DingTalkLeave> findAllActive(Pageable pageable);

    @Query("SELECT COUNT(l) FROM DingTalkLeave l WHERE l.deleted = false")
    long countActive();

    @Query("SELECT COUNT(l) FROM DingTalkLeave l WHERE l.deleted = false AND l.startTime >= ?1 AND l.endTime <= ?2")
    long countByRange(Instant start, Instant end);

    /**
     * V4.34: 找某用户某日覆盖的请假记录 (按日窗口过滤, end_time 可空 - 加班/补卡场景)
     *   - 闭区间过滤: start < dayEnd AND (end IS NULL OR end > dayStart)
     *   - 用法: 在 service 层 dayStart=当天 00:00, dayEnd=次日 00:00 (用 zone 转)
     * @param userid   钉钉 userid
     * @param dayStart 当天 00:00:00 (Instant, 系统时区)
     * @param dayEnd   次日 00:00:00 (Instant, 系统时区)
     */
    @Query("""
           SELECT l FROM DingTalkLeave l
           WHERE l.userid = :userid
             AND l.deleted = false
             AND l.status = 'NORMAL'
             AND l.startTime < :dayEnd
             AND (l.endTime IS NULL OR l.endTime > :dayStart)
           ORDER BY l.startTime ASC
           """)
    List<DingTalkLeave> findCovering(@Param("userid") String userid,
                                     @Param("dayStart") Instant dayStart,
                                     @Param("dayEnd") Instant dayEnd);
}
