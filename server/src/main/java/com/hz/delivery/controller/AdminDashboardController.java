package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final StatsService statsService;

    /**
     * 数据看板：订单分布、名单领取率、SLA 预警、单位/套餐维度、近 14 天趋势、库存预警
     */
    @GetMapping("/dashboard")
    public ApiResult<Map<String, Object>> dashboard() {
        return ApiResult.ok(statsService.dashboard());
    }

    /**
     * 报表中心
     */
    @GetMapping("/reports")
    public ApiResult<Map<String, Object>> reports() {
        return ApiResult.ok(statsService.report());
    }

    /**
     * 健康检查（供容器探针与运维使用）
     */
    @GetMapping("/ping")
    public ApiResult<Map<String, Object>> ping() {
        return ApiResult.ok(Map.of("service", "hz-delivery-server", "time", java.time.LocalDateTime.now().toString()));
    }
}
