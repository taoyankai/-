package com.hz.delivery.service;

import com.hz.delivery.common.BizException;
import com.hz.delivery.common.PasswordUtil;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.entity.Admin;
import com.hz.delivery.mapper.AdminMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private AdminMapper adminMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    private AdminAuthService service;

    @BeforeEach
    void setUp() {
        BizProperties props = new BizProperties();
        props.setAdminLoginMaxFail(5);
        props.setAdminLoginLockMinutes(10);
        lenient().when(redis.opsForValue()).thenReturn(values);
        service = new AdminAuthService(adminMapper, redis, props);
    }

    @Test
    void loginReturnsMandatoryPasswordChangeFlag() {
        Admin admin = admin("admin", "InitialPass!2026", 1);
        when(redis.hasKey(anyString())).thenReturn(false);
        when(adminMapper.selectByUsername("admin")).thenReturn(admin);

        Map<String, Object> result = service.login(" ADMIN ", "InitialPass!2026", "192.0.2.10");

        assertEquals(Boolean.TRUE, result.get("mustChangePassword"));
        assertNotNull(result.get("token"));
        verify(adminMapper).updateById(admin);
        verify(values).set(startsWith("hz:token:admin:"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void fifthFailureLocksBothUsernameAndIp() {
        when(redis.hasKey(anyString())).thenReturn(false);
        when(adminMapper.selectByUsername("admin")).thenReturn(null);
        when(values.increment(anyString())).thenReturn(5L);

        assertThrows(BizException.class,
                () -> service.login("admin", "wrong", "192.0.2.11"));

        verify(values, times(2)).set(startsWith("hz:login:admin:lock:"), eq("1"),
                eq(10L), eq(TimeUnit.MINUTES));
    }

    @Test
    void changingPasswordClearsMandatoryFlag() {
        Admin admin = admin("admin", "InitialPass!2026", 1);
        when(adminMapper.selectById(1L)).thenReturn(admin);

        service.changePassword(1L, "InitialPass!2026", "NewSecurePass!2026");

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getMustChangePassword());
        assertTrue(PasswordUtil.matches("NewSecurePass!2026", captor.getValue().getPassword()));
    }

    private Admin admin(String username, String password, int mustChangePassword) {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setUsername(username);
        admin.setPassword(PasswordUtil.hash(password));
        admin.setRealName("系统管理员");
        admin.setRole("admin");
        admin.setStatus(1);
        admin.setMustChangePassword(mustChangePassword);
        return admin;
    }
}
