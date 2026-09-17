package com.hz.delivery.common;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * SLA 履约时间计算
 */
public final class SlaUtil {

    private SlaUtil() {}

    /**
     * 距承诺发货截止的剩余天数。
     *
     * 正数向上取整、负数向下取整 —— 因为「还剩 9 天 23 小时」对用户来说就是「还剩 10 天」，
     * 直接用 ChronoUnit.DAYS 截断会导致刚下单就显示「还剩 9 天」，与承诺的 10 日不一致。
     */
    public static Long remainDays(LocalDateTime now, LocalDateTime deadline) {
        if (deadline == null) {
            return null;
        }
        double minutes = ChronoUnit.MINUTES.between(now, deadline);
        double days = minutes / 1440.0;
        return days >= 0 ? (long) Math.ceil(days) : (long) Math.floor(days);
    }

    /**
     * 预警等级：3 超时 / 2 不足 1 天 / 1 不足 3 天 / 0 安全
     */
    public static int warnLevel(LocalDateTime now, LocalDateTime deadline) {
        if (deadline == null) {
            return 0;
        }
        Long days = remainDays(now, deadline);
        if (days == null) {
            return 0;
        }
        if (days < 0) {
            return 3;
        }
        if (days < 1) {
            return 2;
        }
        if (days < 3) {
            return 1;
        }
        return 0;
    }
}
