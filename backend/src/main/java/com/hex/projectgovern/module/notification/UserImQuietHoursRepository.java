package com.hex.projectgovern.module.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserImQuietHoursRepository extends JpaRepository<UserImQuietHours, Long> {

    /** 查某用户的所有 DND 窗口(含禁用的) */
    List<UserImQuietHours> findByUserId(Long userId);

    /** 查某用户已启用的所有 DND 窗口 */
    List<UserImQuietHours> findByUserIdAndEnabledTrue(Long userId);
}
