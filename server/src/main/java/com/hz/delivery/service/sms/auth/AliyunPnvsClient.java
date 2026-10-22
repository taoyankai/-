package com.hz.delivery.service.sms.auth;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.*;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.hz.delivery.config.BizProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** SDK 适配器：不返回/记录原始响应、验证码或密钥；关闭自动重试，避免重复收费。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "hz.delivery.sms-auth", name = "provider", havingValue = "aliyun-pnvs")
public class AliyunPnvsClient {
    private final Client sdk;
    private final BizProperties props;

    @org.springframework.beans.factory.annotation.Autowired
    public AliyunPnvsClient(BizProperties props) throws Exception {
        this(props, createClient(props));
    }

    AliyunPnvsClient(BizProperties props, Client sdk) {
        this.props = props;
        this.sdk = sdk;
    }

    private static Client createClient(BizProperties props) throws Exception {
        var auth = props.getSmsAuth();
        if (!StringUtils.hasText(auth.getAccessKeyId()) || !StringUtils.hasText(auth.getAccessKeySecret())
                || !StringUtils.hasText(auth.getSignName()) || !StringUtils.hasText(auth.getTemplateCode())
                || !StringUtils.hasText(auth.getSchemeName()) || auth.getSchemeName().length() > 20
                || props.getCodeTtl() != 300) {
            throw new IllegalStateException("阿里云短信认证配置不完整：检查密钥、签名、模板、方案名（1-20字符）；有效期须为300秒");
        }
        return new Client(new Config().setAccessKeyId(auth.getAccessKeyId())
                .setAccessKeySecret(auth.getAccessKeySecret()).setEndpoint("dypnsapi.aliyuncs.com")
                .setProtocol("https").setConnectTimeout(5000).setReadTimeout(10000));
    }

    private RuntimeOptions runtime() {
        return new RuntimeOptions().setAutoretry(false).setConnectTimeout(5000).setReadTimeout(10000);
    }

    public void send(String phone, String outId) {
        var auth = props.getSmsAuth();
        var request = new SendSmsVerifyCodeRequest().setPhoneNumber(phone).setCountryCode("86")
                .setSchemeName(auth.getSchemeName()).setSignName(auth.getSignName())
                .setTemplateCode(auth.getTemplateCode()).setTemplateParam("{\"code\":\"##code##\",\"min\":\"5\"}")
                .setCodeLength(6L).setCodeType(1L).setValidTime(300L).setInterval(60L)
                .setDuplicatePolicy(1L).setReturnVerifyCode(false).setOutId(outId);
        SendSmsVerifyCodeResponse response;
        try {
            response = sdk.sendSmsVerifyCodeWithOptions(request, runtime());
        } catch (Exception e) {
            log.warn("阿里云验证码发送调用异常 type={} code={}", e.getClass().getSimpleName(),
                    e instanceof TeaException tea ? safeCode(tea.getCode()) : "TRANSPORT_ERROR");
            throw new SmsVerificationException(true);
        }
        var body = response == null ? null : response.getBody();
        if (body == null) throw new SmsVerificationException(true);
        if (!"OK".equals(body.getCode()) || !Boolean.TRUE.equals(body.getSuccess())) {
            log.warn("阿里云验证码发送未受理 code={}", safeCode(body.getCode()));
            throw new SmsVerificationException(false);
        }
    }

    public boolean verify(String phone, String code, String outId) {
        CheckSmsVerifyCodeResponse response;
        try {
            response = sdk.checkSmsVerifyCodeWithOptions(new CheckSmsVerifyCodeRequest()
                    .setPhoneNumber(phone).setCountryCode("86").setSchemeName(props.getSmsAuth().getSchemeName())
                    .setVerifyCode(code).setOutId(outId), runtime());
        } catch (TeaException e) {
            // 阿里云 SDK 会将错误、到期或已覆盖的验证码作为业务异常抛出。
            if ("isv.ValidateFail".equals(e.getCode())) return false;
            log.warn("阿里云验证码核验调用异常 type=TeaException code={}", safeCode(e.getCode()));
            throw new SmsVerificationException(false);
        } catch (Exception e) {
            log.warn("阿里云验证码核验调用异常 type={}", e.getClass().getSimpleName());
            throw new SmsVerificationException(false);
        }
        var body = response == null ? null : response.getBody();
        if (body != null && "isv.ValidateFail".equals(body.getCode())) return false;
        if (body == null || !"OK".equals(body.getCode()) || !Boolean.TRUE.equals(body.getSuccess())
                || body.getModel() == null) throw new SmsVerificationException(false);
        String result = body.getModel().getVerifyResult();
        if (body.getModel().getOutId() != null && !outId.equals(body.getModel().getOutId())) {
            throw new SmsVerificationException(false);
        }
        if ("PASS".equals(result)) return true;
        if ("UNKNOWN".equals(result)) return false;
        throw new SmsVerificationException(false);
    }

    private String safeCode(String code) {
        return code != null && code.matches("[A-Za-z0-9_.-]{1,80}") ? code : "UNKNOWN";
    }
}
