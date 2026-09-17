package com.hz.delivery.common;

/**
 * 全局常量
 */
public final class Constants {

    private Constants() {}

    /** Redis Key 前缀 */
    public static final String KEY_CLIENT_TOKEN = "hz:token:client:";
    public static final String KEY_ADMIN_TOKEN = "hz:token:admin:";
    public static final String KEY_SMS_CODE = "hz:sms:code:";
    public static final String KEY_STOCK_LOCK = "hz:lock:stock:";

    /** 请求头 */
    public static final String HEADER_TOKEN = "X-Token";

    /** 请求属性：当前登录者 */
    public static final String ATTR_GRANTEE_ID = "currentGranteeId";
    public static final String ATTR_ADMIN_ID = "currentAdminId";

    /** 有效期 */
    public static final long CLIENT_TOKEN_TTL_SECONDS = 7 * 24 * 3600L;
    public static final long ADMIN_TOKEN_TTL_SECONDS = 12 * 3600L;

    /** 订单状态 */
    public static final int ORDER_PENDING = 0;      // 待发货
    public static final int ORDER_SHIPPED = 10;     // 已发货
    public static final int ORDER_TRANSIT = 20;     // 运输中
    public static final int ORDER_DELIVERING = 30;  // 派送中
    public static final int ORDER_SIGNED = 40;      // 已签收
    public static final int ORDER_CANCELED = 50;    // 已取消
    public static final int ORDER_EXCEPTION = 60;   // 配送异常
    public static final int ORDER_RETURNED = 70;    // 已退回

    /** 名单导入批次状态 */
    public static final int BATCH_IMPORTING = 0;
    public static final int BATCH_DONE = 1;
    public static final int BATCH_FAILED = 2;
}
