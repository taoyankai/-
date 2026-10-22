package com.hz.delivery.service.sms.auth;

import com.hz.delivery.config.BizProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hz.delivery.sms-auth", name = "provider", havingValue = "aliyun-pnvs")
public class AliyunSmsVerificationService implements SmsVerificationService {
    private final AliyunPnvsClient client;
    private final SmsVerificationStore sessions;
    private final BizProperties props;

    public String provider() { return "aliyun-pnvs"; }
    private String scope() { return provider() + ":" + props.getSmsAuth().getSchemeName(); }

    public SendCodeResult sendCode(String phone) {
        return sessions.withLock(scope(), phone, session -> {
            String outId = UUID.randomUUID().toString().replace("-", "");
            try {
                client.send(phone, outId);
            } catch (SmsVerificationException e) {
                // 请求超时可能已经发送；保留流水以便用户收到后仍能核验。
                if (e.isDeliveryUncertain()) session.save(outId, props.getCodeTtl());
                throw e;
            }
            session.save(outId, props.getCodeTtl());
            return new SendCodeResult(props.getCodeTtl(), provider(), null);
        });
    }

    public boolean verifyCode(String phone, String code) {
        if (code == null || !code.matches("[0-9]{6}")) return false;
        return sessions.withLock(scope(), phone, session -> {
            String outId = session.get();
            if (outId == null) return false;
            return client.verify(phone, code, outId) && session.consume(outId);
        });
    }
}
