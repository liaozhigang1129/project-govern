package com.hex.projectgovern.module.finance.event;

import java.time.LocalDate;

/**
 * F3: 成本月结事件 — T-03 触发钩子
 *
 * 由 CostItemService.monthlySettle() 在事务提交后 publish。
 * ReconciliationEventListener 订阅,对账整 project。
 */
public record CostMonthlySettledEvent(Long projectId, LocalDate period, Long operatorUserId) {}
