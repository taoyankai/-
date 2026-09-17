package com.hz.delivery.common;

import lombok.Getter;

/**
 * 业务异常：由 GlobalExceptionHandler 统一转为 ApiResult
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode ec) {
        super(ec.getMessage());
        this.code = ec.getCode();
    }

    public BizException(ErrorCode ec, String message) {
        super(message);
        this.code = ec.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of(ErrorCode ec) {
        return new BizException(ec);
    }

    public static BizException of(ErrorCode ec, String message) {
        return new BizException(ec, message);
    }
}
