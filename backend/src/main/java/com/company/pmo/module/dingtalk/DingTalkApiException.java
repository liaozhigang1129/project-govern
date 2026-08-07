package com.company.pmo.module.dingtalk;

/** 钉钉 OpenAPI 业务错误 (非 2xx / 业务码非 0) */
public class DingTalkApiException extends RuntimeException {
    public DingTalkApiException(String msg) { super(msg); }
    public DingTalkApiException(String msg, Throwable t) { super(msg, t); }
}
