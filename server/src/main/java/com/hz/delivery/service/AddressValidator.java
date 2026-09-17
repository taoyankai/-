package com.hz.delivery.service;

import java.util.regex.Pattern;

/**
 * 配送地址完整度校验。
 *
 * 招标要求「快递上楼到家、入户配送，不得投放代收点」，因此这里对地址的要求比普通电商更严：
 * 必须精确到楼栋 / 单元 / 门牌，否则不予受理。
 * 校验规则与小程序端 validateAddress 保持一致，前端拦截只是体验优化，服务端才是最终防线。
 */
public final class AddressValidator {

    private AddressValidator() {}

    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final Pattern HAS_NUMBER = Pattern.compile("\\d");
    /** 楼栋/门牌类关键词 */
    private static final Pattern HAS_DOOR = Pattern.compile("(室|栋|幢|号楼|单元|号|巷|组|村|大厦|公寓|小区|组团|座)");

    /**
     * @return null 表示校验通过；否则返回给用户看的错误提示
     */
    public static String validate(String receiver, String phone,
                                  String province, String city, String district,
                                  String detail) {
        if (isBlank(receiver)) {
            return "请填写收货人姓名";
        }
        if (isBlank(phone) || !PHONE.matcher(phone.trim()).matches()) {
            return "请填写正确的 11 位手机号";
        }
        if (isBlank(province) || isBlank(city) || isBlank(district)) {
            return "请选择完整的省 / 市 / 区信息";
        }
        if (isBlank(detail)) {
            return "请填写详细地址";
        }
        String d = detail.replaceAll("\\s", "");
        if (d.length() < 8) {
            return "详细地址太短，请补充到楼栋与门牌号，例：珞喻路1037号3栋502室";
        }
        if (!HAS_NUMBER.matcher(d).find()) {
            return "详细地址缺少门牌号，请补充数字门牌，以免快递无法上门";
        }
        if (!HAS_DOOR.matcher(d).find()) {
            return "详细地址缺少楼栋/门牌信息，请补充「X栋X单元XXX室」等内容";
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
