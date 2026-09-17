package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.common.Constants;
import com.hz.delivery.entity.SlaAlert;
import com.hz.delivery.service.AdminAuthService;
import com.hz.delivery.service.OperationLogService;
import com.hz.delivery.service.SlaAlertService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台 - SLA 发货预警。
 *
 * <p>定时任务每日自动扫描；这里额外提供手动触发，便于部署后立即验证，
 * 而不必等到次日 09:30。
 */
@RestController
@RequestMapping("/api/admin/sla")
@RequiredArgsConstructor
public class AdminSlaController {

    private final SlaAlertService slaAlertService;
    private final OperationLogService operationLogService;
    private final AdminAuthService adminAuthService;

    /**
     * 最近预警记录
     */
    @GetMapping("/alerts")
    public ApiResult<List<SlaAlert>> alerts(@RequestParam(required = false) Integer days) {
        return ApiResult.ok(slaAlertService.recent(days));
    }

    /**
     * 手动触发扫描。
     *
     * @param notify 是否同时发送短信告警，默认否 —— 手动验证时不该打扰库管
     */
    @PostMapping("/scan")
    public ApiResult<Map<String, Object>> scan(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
                                               @RequestParam(defaultValue = "false") boolean notify,
                                               HttpServletRequest request) {
        Map<String, Object> r = slaAlertService.scan(notify);
        operationLogService.success(adminId, "sla", "scan",
                "SLA 发货预警扫描",
                "已超期 " + r.get("overdue") + " 笔，24 小时内到期 " + r.get("dueIn24h")
                        + " 笔，72 小时内到期 " + r.get("dueIn72h") + " 笔，短信通知=" + r.get("notified"),
                (Integer) r.get("pendingTotal"), request);
        return ApiResult.ok(r);
    }
}
