package com.hz.delivery.service;

import com.hz.delivery.common.BizException;
import com.hz.delivery.common.Constants;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.entity.Grantee;
import com.hz.delivery.mapper.GranteeMapper;
import com.hz.delivery.service.sms.auth.SmsVerificationException;
import com.hz.delivery.service.sms.auth.SmsVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final GranteeMapper mapper = mock(GranteeMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SmsVerificationService verification = mock(SmsVerificationService.class);
    private final BizProperties props = new BizProperties();
    private AuthService service;
    private static final String PHONE = "19900000001";

    @BeforeEach void setup() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.getExpire(anyString(), eq(TimeUnit.MINUTES))).thenReturn(-2L);
        Grantee grantee = new Grantee();
        grantee.setId(1L); grantee.setPhone(PHONE); grantee.setName("test"); grantee.setStatus(1);
        when(mapper.selectByPhone(PHONE)).thenReturn(grantee);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(values.increment(anyString())).thenReturn(1L);
        when(verification.provider()).thenReturn("aliyun-pnvs");
        service = new AuthService(mapper, redis, props, verification);
    }

    @Test void unlistedOrDisabledRecipientNeverCallsProvider() {
        when(mapper.selectByPhone(PHONE)).thenReturn(null);
        assertEquals(1001, assertThrows(BizException.class, () -> service.sendCode(PHONE)).getCode());
        Grantee disabled = new Grantee(); disabled.setStatus(0);
        when(mapper.selectByPhone(PHONE)).thenReturn(disabled);
        assertEquals(1002, assertThrows(BizException.class, () -> service.sendCode(PHONE)).getCode());
        verifyNoInteractions(verification);
    }

    @Test void cooldownAndDailyLimitBlockBeforeSending() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        assertEquals(1004, assertThrows(BizException.class, () -> service.sendCode(PHONE)).getCode());
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(values.increment(anyString())).thenReturn(11L);
        assertEquals(1004, assertThrows(BizException.class, () -> service.sendCode(PHONE)).getCode());
        verify(verification, never()).sendCode(anyString());
    }

    @Test void providerOutageDoesNotCountAsWrongCodeOrCreateToken() {
        when(verification.verifyCode(PHONE, "123456")).thenThrow(new SmsVerificationException(false));
        assertEquals(1010, assertThrows(BizException.class, () -> service.login(PHONE, "123456", null)).getCode());
        verify(values, never()).increment(startsWith("hz:login:fail:"));
        verify(values, never()).set(startsWith(Constants.KEY_CLIENT_TOKEN), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test void wrongCodeCountsAndFifthAttemptLocks() {
        assertEquals(1003, assertThrows(BizException.class, () -> service.login(PHONE, "999999", null)).getCode());
        when(values.increment("hz:login:fail:" + PHONE)).thenReturn(5L);
        assertEquals(1006, assertThrows(BizException.class, () -> service.login(PHONE, "999999", null)).getCode());
        verify(values).set("hz:login:lock:" + PHONE, "1", 10, TimeUnit.MINUTES);
    }

    @Test void lockedAccountNeverCallsCloud() {
        when(redis.getExpire(anyString(), eq(TimeUnit.MINUTES))).thenReturn(2L);
        assertEquals(1006, assertThrows(BizException.class, () -> service.login(PHONE, "123456", null)).getCode());
        verify(verification, never()).verifyCode(anyString(), anyString());
    }

    @Test void successCreatesOwnTokenAndNeverLeaksMockHintForHostedProvider() {
        when(verification.verifyCode(PHONE, "123456")).thenReturn(true);
        var login = service.login(PHONE, "123456", null);
        assertEquals(32, login.getToken().length());
        verify(values).set(eq(Constants.KEY_CLIENT_TOKEN + login.getToken()), eq("1"),
                eq(Constants.CLIENT_TOKEN_TTL_SECONDS), eq(TimeUnit.SECONDS));
        assertFalse(service.loginModeInfo().containsKey("mockCode"));
        when(verification.sendCode(PHONE)).thenReturn(new SmsVerificationService.SendCodeResult(300, "aliyun-pnvs", null));
        assertFalse(service.sendCode(PHONE).containsKey("mockCode"));
    }

    @Test void definiteSendFailureRollsBackQuotaButTimeoutDoesNot() {
        when(verification.sendCode(PHONE)).thenThrow(new SmsVerificationException(false));
        assertThrows(SmsVerificationException.class, () -> service.sendCode(PHONE));
        verify(values).decrement(startsWith("hz:sms:daily:"));
        clearInvocations(values);
        doThrow(new SmsVerificationException(true)).when(verification).sendCode(PHONE);
        assertThrows(SmsVerificationException.class, () -> service.sendCode(PHONE));
        verify(values, never()).decrement(anyString());
    }
}
