package com.hex.projectgovern.module.finance.event;

/**
 * F3: 发票入账完成事件 — T-03 触发钩子
 *
 * 由 InvoiceService.confirm() 在事务提交后 publish。
 * ReconciliationEventListener 订阅,触发对账。
 */
public record InvoiceConfirmedEvent(Long invoiceId, Long operatorUserId) {}
