package com.hz.delivery.service.sms.auth;

import com.hz.delivery.config.BizProperties;
import com.hz.delivery.service.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hz.delivery.sms-auth", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalSmsVerificationService implements SmsVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SmsSender sender;
    private final SmsVerificationStore sessions;
    private final BizProperties props;

    public String provider() { return sender.provider(); }
    public String mockCodeHint() {
        return "mock".equalsIgnoreCase(sender.provider()) && StringUtils.hasText(props.getMockSmsCode())
                ? props.getMockSmsCode() : null;
    }

    public SendCodeResult sendCode(String phone) {
        return sessions.withLock("local:" + provider(), phone, session -> {
            String code = mockCodeHint() != null ? mockCodeHint() : String.format("%06d", RANDOM.nextInt(1000000));
            try {
                sender.sendCode(phone, code);
            } catch (RuntimeException e) {
                // 旧通道未区分超时与拒绝，保守保留频控，避免重发。
                throw new SmsVerificationException(true);
            }
            session.save(UUID.randomUUID() + ":" + code, props.getCodeTtl());
            return new SendCodeResult(props.getCodeTtl(), provider(), mockCodeHint());
        });
    }

    public boolean verifyCode(String phone, String code) {
        return sessions.withLock("local:" + provider(), phone, session -> {
            String stored = session.get();
            return stored != null && code != null && stored.endsWith(":" + code)
                    && !code.contains(":") && session.consume(stored);
        });
    }
}
