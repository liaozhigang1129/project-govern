package com.company.zhiyu.module.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationWriteService 单元测试(P2-C)。
 *
 * 覆盖:
 *  - writeOne: 正常路径 → 入库 + 返回带 id 的实体
 *  - writeOne: recipientId = null → 跳过(返回 null,不抛)
 *  - writeAll: 批量 + 跳过 null 项 + 返回实际写入条数
 *  - 多次调用不会重复写(无去重键,但每次都是新行 — 文档化设计)
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@EnableConfigurationProperties(MailProperties.class)
@Import(NotificationWriteService.class)
class NotificationWriteServiceTest {

    @Autowired NotificationRepository repo;
    @Autowired NotificationWriteService svc;

    @BeforeEach
    void clean() { repo.deleteAll(); }

    @Test
    @DisplayName("writeOne: 正常 → 1 行 UNREAD + 返回 entity")
    void writeOne_ok() {
        Notification n = svc.writeOne(1L, "INITIATION_SUBMIT", 100L, "IR-2026-001",
                "立项待审批: IR-2026-001", "请审批");
        assertThat(n).isNotNull();
        assertThat(n.getId()).isNotNull();
        assertThat(n.getStatus()).isEqualTo(Notification.NotificationStatus.UNREAD);
        assertThat(n.getRecipientId()).isEqualTo(1L);
        assertThat(repo.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("writeOne: recipientId=null → 跳过(返回 null,不入库)")
    void writeOne_nullRecipient_skip() {
        Notification n = svc.writeOne(null, "X", 1L, "X-1", "t", "c");
        assertThat(n).isNull();
        assertThat(repo.count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("writeAll: 批量,跳过 null 项,返回实际写入条数")
    void writeAll_skipNulls() {
        int n = svc.writeAll(java.util.List.of(
                new NotificationWriteService.OneWrite(1L, "C1", 10L, "C-1", "t1", "c1"),
                new NotificationWriteService.OneWrite(null, "C2", 20L, "C-2", "t2", "c2"),
                new NotificationWriteService.OneWrite(3L, "C3", 30L, "C-3", "t3", "c3")
        ));
        assertThat(n).isEqualTo(2);
        assertThat(repo.count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("writeAll: 空列表 → 0")
    void writeAll_empty() {
        int n = svc.writeAll(java.util.List.of());
        assertThat(n).isZero();
        assertThat(repo.count()).isZero();
    }

    @Test
    @DisplayName("写 1 个用户 2 条不同 category → 都入库(不去重,这是设计)")
    void writeSameUserDifferentCategories() {
        svc.writeOne(1L, "A", 1L, "X", "t", "c");
        svc.writeOne(1L, "B", 2L, "Y", "t", "c");
        assertThat(repo.countByRecipientIdAndStatus(1L, Notification.NotificationStatus.UNREAD))
                .isEqualTo(2L);
    }
}
