package com.hz.delivery.service.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 阿里云短信通道骨架。
 *
 * 当 `hz.delivery.sms.provider = aliyun` 时启用。
 *
 * 【接入步骤】
 *  1. pom.xml 引入依赖：
 *       com.aliyun:dysmsapi20170525（阿里云短信）
 *  2. application.yml 增加：
 *       hz.delivery.sms.aliyun.access-key-id / access-key-secret / sign-name / template-code
 *     （建议通过环境变量注入，不要硬编码密钥）
 *  3. 在本类构造一个 dysmsapi Client，并在 send() 中调用 SendSms，
 *     模板变量按模板约定填入，例如 {"code":"123456"}
 *  4. 同时把 `hz.delivery.mock-sms-code` 置空，验证码将改为随机 6 位
 *
 * 在补全 SDK 调用之前，send() 会显式抛出异常，避免「看似发出、实际没发」的静默失败。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "hz.delivery.sms.provider", havingValue = "aliyun")
public class AliyunSmsSender implements SmsSender {

    @Override
    public String provider() {
        return "aliyun";
    }

    @Override
    public String sendCode(String phone, String code) {
        // TODO: 接入阿里云短信 SDK，例如：
        //   Config config = new Config().setAccessKeyId(...).setAccessKeySecret(...);
        //   Client client = new Client(config);
        //   SendSmsRequest req = SendSmsRequest.builder()
        //       .phoneNumbers(phone)
        //       .signName(signName)
        //       .templateCode(templateCode)
        //       .templateParam("{\"code\":\"" + code + "\"}")
        //       .build();
        //   client.sendSms(req);
        log.error("[短信-阿里云通道] 尚未接入 SDK，无法真实发送 phone={}", phone);
        throw new UnsupportedOperationException(
                "阿里云短信通道尚未接入：请在 AliyunSmsSender 中补全 SDK 调用，"
                        + "或先将 hz.delivery.sms.provider 改回 mock / hbu");
    }

    @Override
    public String sendText(String phone, String text) {
        log.error("[短信-阿里云通道] 尚未接入 SDK，无法真实发送 phone={}", phone);
        throw new UnsupportedOperationException(
                "阿里云短信通道尚未接入：请在 AliyunSmsSender 中补全 SDK 调用，"
                        + "或先将 hz.delivery.sms.provider 改回 mock / hbu");
    }
}
