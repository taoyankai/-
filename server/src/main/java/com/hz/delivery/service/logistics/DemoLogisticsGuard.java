package com.hz.delivery.service.logistics;

import com.hz.delivery.entity.Order;

import java.util.Locale;

/** 演示标识使用保留前缀 DEMO，禁止演示运单调用真实物流平台。 */
final class DemoLogisticsGuard {

    private DemoLogisticsGuard() {
    }

    static boolean isDemo(Order order) {
        return order != null && (hasDemoPrefix(order.getOrderNo())
                || hasDemoPrefix(order.getEmpNo()) || hasDemoPrefix(order.getWaybillNo()));
    }

    static boolean hasDemoPrefix(String value) {
        return value != null && value.trim().toUpperCase(Locale.ROOT).startsWith("DEMO");
    }
}
