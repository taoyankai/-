package com.hz.delivery.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBiz(BizException e) {
        log.warn("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ApiResult<Void> handleValid(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ApiResult.fail(ErrorCode.PARAM_ERROR, msg.isEmpty() ? ErrorCode.PARAM_ERROR.getMessage() : msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResult<Void> handleMissing(MissingServletRequestParameterException e) {
        return ApiResult.fail(ErrorCode.PARAM_ERROR, "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResult<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        return ApiResult.fail(ErrorCode.EXCEL_TOO_LARGE);
    }

    /** 数据库唯一约束是并发写入的最终防线；将约束名转换为可理解的业务提示。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResult<Void> handleDuplicateKey(DuplicateKeyException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        log.warn("唯一约束冲突：{}", e.getMessage());
        if (message.contains("uk_order_waybill_no")) {
            return ApiResult.fail(ErrorCode.WAYBILL_DUPLICATE);
        }
        if (message.contains("uk_grantee_phone")) {
            return ApiResult.fail(ErrorCode.PARAM_ERROR, "该手机号已存在（包括已删除的历史记录）");
        }
        if (message.contains("uk_admin_username")) {
            return ApiResult.fail(ErrorCode.PARAM_ERROR, "管理员账号已存在");
        }
        if (message.contains("uk_order_order_no")) {
            return ApiResult.fail(ErrorCode.PARAM_ERROR, "订单号生成冲突，请重新提交");
        }
        if (message.contains("uk_carrier_code")) {
            return ApiResult.fail(ErrorCode.PARAM_ERROR, "承运商编码已存在");
        }
        if (message.contains("uk_import_batch_no")) {
            return ApiResult.fail(ErrorCode.PARAM_ERROR, "导入批次号生成冲突，请重试");
        }
        return ApiResult.fail(ErrorCode.PARAM_ERROR, "数据已存在，请勿重复提交");
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return ApiResult.fail(ErrorCode.SERVER_ERROR);
    }
}
