package com.hz.delivery.common;

import lombok.Getter;

/**
 * 业务错误码
 */
@Getter
public enum ErrorCode {

    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "登录已失效，请重新登录"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "数据不存在"),
    SERVER_ERROR(500, "服务器繁忙，请稍后重试"),

    // 身份核验 1000+
    PHONE_NOT_IN_LIST(1001, "该手机号不在本次慰问品领取名单内，请联系本单位工会核实"),
    GRANTEE_DISABLED(1002, "您的领取资格已停用，请联系本单位工会"),
    CODE_ERROR(1003, "验证码错误或已失效"),
    CODE_TOO_FREQUENT(1004, "验证码发送过于频繁，请稍后再试"),
    NAME_MISMATCH(1005, "姓名与领取名单登记不一致，请填写本人真实姓名"),
    LOGIN_LOCKED(1006, "身份核验失败次数过多，请稍后再试"),
    SMS_NOT_REQUIRED(1007, "当前登录方式无需短信验证码，请填写手机号与姓名登录"),
    SMS_TEST_DISABLED(1008, "短信自检接口未启用（需设置 SMS_TEST_ENABLED=true）"),
    SMS_SEND_FAILED(1009, "短信发送失败，请联系管理员检查短信通道配置"),

    // 套餐 1100+
    PACKAGE_OFFLINE(1101, "该套餐已下架，请重新选择"),
    PACKAGE_NO_STOCK(1102, "该套餐库存不足，请选择其他套餐"),
    QUOTA_EXHAUSTED(1103, "您已领取过慰问品，每人限领 1 份，不可重复领取"),
    PACKAGE_REQUIRED(1104, "请选择一个套餐"),

    // 地址 1200+
    ADDRESS_INCOMPLETE(1201, "配送地址不完整，请填写到具体门牌号（如 x 栋 x 单元 x 室），以便送货上门"),
    ADDRESS_REQUIRED(1202, "请填写收货人姓名、手机号与详细地址"),
    PHONE_FORMAT_ERROR(1203, "手机号格式不正确"),

    // 订单 1300+
    ORDER_NOT_FOUND(1301, "订单不存在"),
    ORDER_STATUS_ERROR(1302, "当前订单状态不允许该操作"),
    ORDER_NO_PERMISSION(1303, "无权操作该订单"),
    ORDER_DUPLICATE(1304, "请勿重复提交，您已有相同的待发货订单"),
    ORDER_ADDR_LIMIT(1305, "收货地址仅可自行修改 1 次，如需再次修改请联系配送中心客服"),
    ORDER_ADDR_LOCKED(1306, "当前订单状态不可自行修改收货地址（仅待发货可改），已发货请联系配送中心客服"),

    // 发货 1400+
    WAYBILL_DUPLICATE(1401, "运单号已存在，请勿重复录入"),
    CARRIER_REQUIRED(1402, "请选择承运商"),
    WAYBILL_REQUIRED(1403, "请填写运单号"),

    // 导入导出 1500+
    EXCEL_EMPTY(1501, "文件内容为空，请检查后重新上传"),
    EXCEL_FORMAT_ERROR(1502, "文件格式不正确，请使用系统提供的模板"),
    EXCEL_TOO_LARGE(1503, "文件行数超过上限（单次最多 5000 行），请分批导入");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
