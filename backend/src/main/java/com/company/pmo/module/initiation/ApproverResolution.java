package com.company.pmo.module.initiation;

/**
 * 审批人解析结果(独立文件,因为 Java 不允许 public record 在非同名 .java 中)。
 *  - userId: 实际收件审批人 id(主审批人或 backup)
 *  - onBehalfOfUserId: 代审时记录原主审批人 id(供 audit 追溯)
 */
public record ApproverResolution(Long userId, Long onBehalfOfUserId) {
    public static final ApproverResolution EMPTY = new ApproverResolution(null, null);
}
