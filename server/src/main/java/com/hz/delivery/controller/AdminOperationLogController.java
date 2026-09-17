package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.common.PageResult;
import com.hz.delivery.entity.OperationLog;
import com.hz.delivery.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 - 操作审计日志查询。
 *
 * <p>只读接口：管理员能查「谁在什么时候导出了多少条含手机号的名单」，也是合规检查时
 * 需要出示的记录。
 */
@RestController
@RequestMapping("/api/admin/operation-logs")
@RequiredArgsConstructor
public class AdminOperationLogController {

    private final OperationLogService operationLogService;

    @GetMapping
    public ApiResult<PageResult<OperationLog>> page(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) String module,
                                                    @RequestParam(required = false) String action,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String date) {
        return ApiResult.ok(operationLogService.page(page, size, module, action, keyword, date));
    }
}
