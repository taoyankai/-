package com.hz.delivery.controller;

import com.hz.delivery.common.BizException;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.service.AuthService;
import com.hz.delivery.service.sms.SmsSender;
import com.hz.delivery.service.sms.auth.SmsVerificationService;
import com.hz.delivery.vo.ClientLoginVO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSmsControllerTest {
    @Test void independentGateBlocksCallsAndSuccessfulTestDiscardsToken() {
        var sender = mock(SmsSender.class);
        var auth = mock(AuthService.class);
        var verification = mock(SmsVerificationService.class);
        var props = new BizProperties();
        var controller = new AdminSmsController(sender, props, auth, verification);
        assertEquals(1008, assertThrows(BizException.class,
                () -> controller.authSendCode("19900000001")).getCode());
        verifyNoInteractions(auth);
        props.getSmsAuth().setTestEnabled(true);
        when(auth.smsMode()).thenReturn(true);
        var login = new ClientLoginVO(); login.setToken("temporary-token");
        when(auth.login("19900000001", "123456", null)).thenReturn(login);
        when(verification.provider()).thenReturn("aliyun-pnvs");
        var result = controller.authVerifyCode("19900000001", "123456");
        assertEquals(true, result.getData().get("verified"));
        assertFalse(result.getData().containsKey("token"));
        verify(auth).logout("temporary-token");
        verifyNoInteractions(sender);
        assertFalse(props.getSms().isTestEnabled());
    }
}
