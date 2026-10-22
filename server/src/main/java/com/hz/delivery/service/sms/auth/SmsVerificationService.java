package com.hz.delivery.service.sms.auth;

public interface SmsVerificationService {
    record SendCodeResult(long ttl, String provider, String mockCode) {}
    SendCodeResult sendCode(String phone);
    /** 仅验证码不匹配或过期返回 false；服务故障必须抛出异常。成功时消费会话。 */
    boolean verifyCode(String phone, String code);
    String provider();
    default String mockCodeHint() { return null; }
}
