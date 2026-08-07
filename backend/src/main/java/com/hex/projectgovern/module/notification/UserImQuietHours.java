package com.hex.projectgovern.module.notification;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 用户 IM 勿扰时段(P2 #2)。
 *
 * 一行 = 一个时间窗口:
 *  - start_time/end_time "HH:mm" 24h 字符串
 *  - end &lt; start → 跨午夜(如 22:00 ~ 08:00)
 *  - 同一人可建多个窗口(午餐 + 深夜)
 *  - enabled=false → 暂停该窗口(保留)
 *  - timezone 字段: V2.1 启用, MVP 固定 Asia/Shanghai
 */
@Entity
@Table(name = "user_im_quiet_hours", indexes = {
        @Index(name = "ix_quiet_hours_user", columnList = "user_id,enabled")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserImQuietHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "start_time", nullable = false, length = 5)
    private String startTime;

    @Column(name = "end_time", nullable = false, length = 5)
    private String endTime;

    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Shanghai";

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
