package com.hex.projectgovern.module.finance.event;

/**
 * F3: 付款确认事件 — T-03 触发钩子
 *
 * 由 PaymentService.confirm() 在事务提交后 publish。
 * ReconciliationEventListener 订阅,触发对账。
 */
public record PaymentConfirmedEvent(Long paymentId, Long invoiceId, Long operatorUserId) {}
