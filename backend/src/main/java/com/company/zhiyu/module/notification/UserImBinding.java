package com.company.zhiyu.module.notification;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 用户 IM 账号绑定(P2-A)。
 *
 * 一个用户可在多个 IM 平台有不同 external_user_id。
 *  - enabled=false:暂停推送(离职/换号)
 *  - 唯一约束 (user_id, channel): 同一平台一个用户只允许一个绑定
 */
@Entity
@Table(name = "user_im_binding",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_channel", columnNames = {"user_id", "channel"}),
        indexes = {
                @Index(name = "ix_im_binding_user", columnList = "user_id"),
                @Index(name = "ix_im_binding_channel_external", columnList = "channel,external_user_id")
        })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserImBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** app_user.id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** wechat_work / dingtalk / feishu */
    @Column(nullable = false, length = 32)
    private String channel;

    /** IM 平台内用户标识(企微 userid / 钉钉邮箱 / 飞书邮箱) */
    @Column(name = "external_user_id", nullable = false, length = 128)
    private String externalUserId;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
