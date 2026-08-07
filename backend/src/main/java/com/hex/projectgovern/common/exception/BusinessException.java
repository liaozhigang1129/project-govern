package com.hex.projectgovern.common.exception;

import lombok.Getter;

/**
 * 业务异常 — 3 种构造器兼容历史调用
 *
 * <ul>
 *   <li>{@code new BusinessException(String msg)}               — HTTP 400 默认</li>
 *   <li>{@code new BusinessException(int code, String msg)}     — HTTP code + 消息 (UserMgmt 等模块用)</li>
 *   <li>{@code new BusinessException(String errorCode, String msg)} — 业务错误码 + 消息 (Finance 等模块用, P0-A 解锁 finance 编译)</li>
 * </ul>
 *
 * <p>{@code code} 字段是 HTTP 状态码 (返回给前端的 ApiResponse.code);<br>
 * {@code errorCode} 字段是可选的业务错误码字符串 (仅服务端日志/debug 用, 不影响前端契约).
 */
@Getter
public class BusinessException extends RuntimeException {
    /** HTTP 状态码 (返回前端 ApiResponse.code) */
    private final int code;
    /** 业务错误码 (可选, 服务端用; e.g. "CONTRACT_NOT_FOUND") */
    private final String errorCode;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
        this.errorCode = null;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.errorCode = null;
    }

    /**
     * 业务错误码 + 消息 (HTTP 状态码默认 400).
     * 兼容 finance 等模块用法 {@code new BusinessException("CONTRACT_NOT_FOUND", "合同不存在 id=" + id)}.
     *
     * @param errorCode 业务错误码 (e.g. "INVALID_CODE"), 仅服务端日志/分类使用
     * @param message   人类可读消息 (返回前端 ApiResponse.message)
     */
    public BusinessException(String errorCode, String message) {
        super(message);
        this.code = 400;
        this.errorCode = errorCode;
    }

    /** 全构造器: HTTP code + 业务 errorCode + message */
    public BusinessException(int code, String errorCode, String message) {
        super(message);
        this.code = code;
        this.errorCode = errorCode;
    }
}
