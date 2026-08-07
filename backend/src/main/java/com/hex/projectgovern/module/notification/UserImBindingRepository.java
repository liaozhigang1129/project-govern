package com.hex.projectgovern.module.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserImBindingRepository extends JpaRepository<UserImBinding, Long> {

    Optional<UserImBinding> findByUserIdAndChannel(Long userId, String channel);

    /** 批量查某 channel 下所有用户的绑定(用于扇出) */
    @Query("SELECT b FROM UserImBinding b " +
            "WHERE b.userId IN :userIds AND b.channel = :channel AND b.enabled = true")
    List<UserImBinding> findByUserIdInAndChannelAndEnabledTrue(
            @Param("userIds") Collection<Long> userIds,
            @Param("channel") String channel);

    /** 查某用户的所有通道绑定 */
    List<UserImBinding> findByUserIdAndEnabledTrue(Long userId);
}
