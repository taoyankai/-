package com.hz.delivery.config;

import com.hz.delivery.mapper.AdminMapper;
import com.hz.delivery.entity.Admin;
import com.hz.delivery.common.Constants;
import org.springframework.data.redis.core.ValueOperations;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    @Test
    @SuppressWarnings("unchecked")
    void smsAuthSelfTestRequiresAdminRole() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        var mapper = mock(AdminMapper.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(Constants.KEY_ADMIN_TOKEN + "test-token")).thenReturn("2");
        var admin = new Admin(); admin.setId(2L); admin.setStatus(1);
        admin.setMustChangePassword(0); admin.setRole("operator");
        when(mapper.selectById(2L)).thenReturn(admin);
        var request = new MockHttpServletRequest("POST", "/api/admin/sms/auth/send-code");
        request.addHeader(Constants.HEADER_TOKEN, "test-token");
        var response = new MockHttpServletResponse();
        assertFalse(new AuthInterceptor(redis, mapper).preHandle(request, response, new Object()));
        org.junit.jupiter.api.Assertions.assertTrue(response.getContentAsString().contains("403"));
    }

    @Test
    void tokenInQueryStringIsRejected() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AdminMapper adminMapper = mock(AdminMapper.class);
        AuthInterceptor interceptor = new AuthInterceptor(redis, adminMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/dashboard");
        request.setParameter("token", "must-not-be-accepted");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verifyNoInteractions(redis, adminMapper);
    }
}
