package com.hz.delivery.service.sms.auth;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.*;
import com.aliyun.teautil.models.RuntimeOptions;
import com.aliyun.tea.TeaException;
import com.hz.delivery.config.BizProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AliyunPnvsClientTest {
    private Client sdk;
    private AliyunPnvsClient client;
    private BizProperties props;

    @BeforeEach
    void setup() {
        sdk = mock(Client.class);
        props = new BizProperties();
        props.getSmsAuth().setSignName("test-sign");
        props.getSmsAuth().setTemplateCode("test-template");
        client = new AliyunPnvsClient(props, sdk);
    }

    @Test
    void cloudGeneratesSixDigitsWithoutReturningCodeOrRetryingSend() throws Exception {
        when(sdk.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse()
                .setBody(new SendSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)));
        client.send("19900000001", "attempt-1");
        var request = ArgumentCaptor.forClass(SendSmsVerifyCodeRequest.class);
        var runtime = ArgumentCaptor.forClass(RuntimeOptions.class);
        verify(sdk).sendSmsVerifyCodeWithOptions(request.capture(), runtime.capture());
        assertEquals(6L, request.getValue().getCodeLength());
        assertEquals(1L, request.getValue().getCodeType());
        assertEquals(300L, request.getValue().getValidTime());
        assertEquals(60L, request.getValue().getInterval());
        assertEquals(1L, request.getValue().getDuplicatePolicy());
        assertFalse(request.getValue().getReturnVerifyCode());
        assertEquals("{\"code\":\"##code##\",\"min\":\"5\"}", request.getValue().getTemplateParam());
        assertEquals(props.getSmsAuth().getSchemeName(), request.getValue().getSchemeName());
        assertEquals("attempt-1", request.getValue().getOutId());
        assertFalse(runtime.getValue().getAutoretry());
    }

    @Test
    void explicitRejectionAndTransportTimeoutHaveDifferentQuotaSemantics() throws Exception {
        when(sdk.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse()
                .setBody(new SendSmsVerifyCodeResponseBody().setCode("FREQUENCY_FAIL").setSuccess(false)));
        assertFalse(assertThrows(SmsVerificationException.class,
                () -> client.send("19900000001", "attempt")).isDeliveryUncertain());
        when(sdk.sendSmsVerifyCodeWithOptions(any(), any())).thenThrow(new SocketTimeoutException());
        assertTrue(assertThrows(SmsVerificationException.class,
                () -> client.send("19900000001", "attempt")).isDeliveryUncertain());
    }

    @Test
    void onlyPassAuthenticatesAndUsesSameSchemeAndAttempt() throws Exception {
        when(sdk.checkSmsVerifyCodeWithOptions(any(), any())).thenReturn(check("UNKNOWN"), check("PASS"));
        assertFalse(client.verify("19900000001", "123456", "attempt-1"));
        assertTrue(client.verify("19900000001", "123456", "attempt-1"));
        var request = ArgumentCaptor.forClass(CheckSmsVerifyCodeRequest.class);
        verify(sdk, times(2)).checkSmsVerifyCodeWithOptions(request.capture(), any());
        assertEquals(props.getSmsAuth().getSchemeName(), request.getValue().getSchemeName());
        assertEquals("attempt-1", request.getValue().getOutId());
    }

    @Test
    void missingModelAndServiceErrorsAreNotInvalidUserCodes() throws Exception {
        when(sdk.checkSmsVerifyCodeWithOptions(any(), any()))
                .thenReturn(new CheckSmsVerifyCodeResponse().setBody(
                        new CheckSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)))
                .thenThrow(new SocketTimeoutException());
        assertThrows(SmsVerificationException.class, () -> client.verify("19900000001", "123456", "attempt"));
        assertThrows(SmsVerificationException.class, () -> client.verify("19900000001", "123456", "attempt"));
    }

    @Test
    void missingCredentialsFailClosedBeforeAnyCall() {
        assertThrows(IllegalStateException.class, () -> new AliyunPnvsClient(props));
        verifyNoInteractions(sdk);
    }

    @Test
    void aliyunValidationFailureIsWrongCodeForBothSdkExceptionAndResponse() throws Exception {
        when(sdk.checkSmsVerifyCodeWithOptions(any(), any()))
                .thenThrow(new TeaException(java.util.Map.of("code", "isv.ValidateFail", "message", "validation failed")))
                .thenReturn(new CheckSmsVerifyCodeResponse().setBody(new CheckSmsVerifyCodeResponseBody()
                        .setCode("isv.ValidateFail").setSuccess(false)));
        assertFalse(client.verify("19900000001", "000000", "attempt"));
        assertFalse(client.verify("19900000001", "000000", "attempt"));
    }

    @Test
    void permissionAndUnknownSdkErrorsStillDoNotCountAsWrongUserCodes() throws Exception {
        when(sdk.checkSmsVerifyCodeWithOptions(any(), any()))
                .thenThrow(new TeaException(java.util.Map.of("code", "isv.RAM_PERMISSION_DENY", "message", "denied")))
                .thenThrow(new TeaException(java.util.Map.of("code", "isp.UNKNOWN", "message", "unknown")));
        assertThrows(SmsVerificationException.class, () -> client.verify("19900000001", "123456", "attempt"));
        assertThrows(SmsVerificationException.class, () -> client.verify("19900000001", "123456", "attempt"));
    }

    private CheckSmsVerifyCodeResponse check(String result) {
        return new CheckSmsVerifyCodeResponse().setBody(new CheckSmsVerifyCodeResponseBody()
                .setCode("OK").setSuccess(true).setModel(new CheckSmsVerifyCodeResponseBody
                        .CheckSmsVerifyCodeResponseBodyModel().setVerifyResult(result)));
    }
}
