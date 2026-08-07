package com.company.pmo.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 操作审计日志(写操作留痕,合规追溯)。
 *
 * <p>字段对应 operation_log 表 (MySQL 8.0,MyISAM/PostgreSQL 通用):
 * <ul>
 *   <li><b>id</b>            - 主键,自增</li>
 *   <li><b>userId</b>        - 操作用户 ID(FK→app_user.id),匿名操作时为 null</li>
 *   <li><b>resourceType</b>  - 资源类型/模块名(32 字符以内,例:USER / PROJECT / INITIATION)</li>
 *   <li><b>resourceId</b>    - 资源 ID(主键,多目标操作如批量删除为 null)</li>
 *   <li><b>action</b>        - 操作动作(32 字符以内,例:CREATE / UPDATE / DELETE / LOGIN / APPROVE)</li>
 *   <li><b>payload</b>       - JSON 字符串,8KB 以内:{request, response, result, error, duration}</li>
 *   <li><b>ipAddress</b>     - 客户端 IP(从 x-forwarded-for 优先读,无则用 request.getRemoteAddr)</li>
 *   <li><b>createdAt</b>     - 操作时间(毫秒精度,DB 默认 CURRENT_TIMESTAMP(3))</li>
 * </ul>
 *
 * <p>索引:
 * <ul>
 *   <li>PRIMARY: id</li>
 *   <li>idx_oplog_user: user_id</li>
 *   <li>idx_oplog_resource: (resource_type, resource_id)</li>
 *   <li>idx_oplog_created: created_at</li>
 * </ul>
 *
 * <p>查询:仅 PMO/ADMIN 角色可读,默认 7 天窗口,分页 size=20,最大 100。
 *
 * @since 2026-Q1 P1.5-d
 */
@Entity
@Table(name = "operation_log")
@Getter
@Setter
@NoArgsConstructor
public class OperationLog {

    /** 主键,自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作用户 ID(FK→app_user.id);未登录或匿名请求时为 null */
    @Column(name = "user_id")
    private Long userId;

    /** 资源类型(32 字符);模块名:USER / PROJECT / MILESTONE / INITIATION / DICTIONARY / DEPT / ROLE */
    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    /** 资源 ID(主键值);批量删除等多目标操作时为 null */
    @Column(name = "resource_id")
    private Long resourceId;

    /** 操作动作(32 字符);CREATE / UPDATE / DELETE / LOGIN / LOGOUT / APPROVE / REJECT / SUPPLEMENT 等 */
    @Column(nullable = false, length = 32)
    private String action;

    /**
     * JSON 字符串,8KB 以内。结构:{request, response, result, error, duration}。
     * <p>实际表字段是 PG 的 <code>jsonb</code> / MySQL 的 <code>json</code>;
     * 显式用 {@code @JdbcTypeCode(SqlTypes.JSON)} 兼容双方言,
     * 而非 {@code columnDefinition} 写死 MySQL 语法。
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String payload;

    /** 客户端 IP,64 字符以内 */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** 操作时间(毫秒精度),DB 默认 CURRENT_TIMESTAMP(3) */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
