package com.hz.delivery.config;

import com.hz.delivery.mapper.AdminMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthInterceptorTest {

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
