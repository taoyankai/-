package com.hz.delivery.service.sms;

/**
 * 短信发送通道。
 *
 * <p>业务代码（AuthService）只依赖本接口，不关心由哪家服务商发送。
 * 更换服务商时新增一个实现类，并按 {@code hz.delivery.sms.provider} 配置启用即可，
 * 无需改动任何调用方代码。
 *
 * <p>目前已内置：
 * <ul>
 *   <li>{@code mock} 演示通道（默认）：不真实发送，仅写日志，便于本地与联调环境使用；</li>
 *   <li>{@code hbu} 河北大学统一短信服务平台：真实发送，已按接口文档接入；</li>
 *   <li>{@code aliyun} 阿里云通道骨架：按 provider=aliyun 启用，需补全 SDK 调用后生效。</li>
 * </ul>
 */
public interface SmsSender {

    /** 通道标识，与 {@code hz.delivery.sms.provider} 取值一致 */
    String provider();

    /**
     * 向指定手机号发送身份核验验证码。
     *
     * @param phone 11 位手机号
     * @param code  6 位数字验证码
     * @return 通道返回的原始响应，便于日志与排查
     * @throws com.hz.delivery.common.BizException 发送失败时抛出，带上通道返回的错误信息
     */
    String sendCode(String phone, String code);

    /**
     * 发送自定义正文短信。用于运维自检（后台「短信通道自检」），不参与业务验证码流程。
     *
     * @return 通道返回的原始响应
     */
    String sendText(String phone, String text);
}
