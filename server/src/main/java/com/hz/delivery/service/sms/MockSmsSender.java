package com.hz.delivery.service.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 演示 / 开发环境的短信通道：不真实发送，仅把验证码写入日志。
 *
 * <p>当 {@code hz.delivery.sms.provider = mock}（或未配置，默认）时启用。
 * 与 {@code hz.delivery.mock-sms-code} 配合：演示环境把验证码固定为 123456，
 * 因此即使不看日志也能登录。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "hz.delivery.sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsSender implements SmsSender {

    private static final String OK = "ok(mock)";

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String sendCode(String phone, String code) {
        log.info("[短信-演示通道] 向 {} 发送验证码 {}（演示环境不真实发送）", phone, code);
        return OK;
    }

    @Override
    public String sendText(String phone, String text) {
        log.info("[短信-演示通道] 向 {} 发送正文「{}」（演示环境不真实发送）", phone, text);
        return OK;
    }
}
