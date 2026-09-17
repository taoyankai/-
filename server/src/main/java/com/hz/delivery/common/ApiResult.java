package com.hz.delivery.common;

import lombok.Data;

/**
 * 统一响应体
 * { "code": 0, "msg": "ok", "data": {...} }
 * code = 0 表示成功，非 0 为业务错误码
 */
@Data
public class ApiResult<T> {

    private int code;
    private String msg;
    private T data;
    private long timestamp = System.currentTimeMillis();

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 0;
        r.msg = "ok";
        r.data = data;
        return r;
    }

    public static <T> ApiResult<T> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> fail(int code, String msg) {
        ApiResult<T> r = new ApiResult<>();
        r.code = code;
        r.msg = msg;
        return r;
    }

    public static <T> ApiResult<T> fail(ErrorCode ec) {
        return fail(ec.getCode(), ec.getMessage());
    }

    public static <T> ApiResult<T> fail(ErrorCode ec, String msg) {
        return fail(ec.getCode(), msg);
    }
}
