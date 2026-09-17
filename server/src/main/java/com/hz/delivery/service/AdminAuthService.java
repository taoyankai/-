package com.hz.delivery.service;

import com.hz.delivery.common.BizException;
import com.hz.delivery.common.Constants;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.common.PasswordUtil;
import com.hz.delivery.entity.Admin;
import com.hz.delivery.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminMapper adminMapper;
    private final StringRedisTemplate redis;

    public Map<String, Object> login(String username, String password) {
        Admin a = adminMapper.selectByUsername(username);
        if (a == null || !PasswordUtil.matches(password, a.getPassword())) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "账号或密码错误");
        }
        if (a.getStatus() == null || a.getStatus() != 1) {
            throw BizException.of(ErrorCode.FORBIDDEN, "账号已停用，请联系管理员");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(Constants.KEY_ADMIN_TOKEN + token, String.valueOf(a.getId()),
                Constants.ADMIN_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);

        a.setLastLoginTime(LocalDateTime.now());
        adminMapper.updateById(a);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("username", a.getUsername());
        result.put("realName", a.getRealName());
        result.put("role", a.getRole());
        result.put("expiresIn", Constants.ADMIN_TOKEN_TTL_SECONDS);
        log.info("后台登录成功 username={}", username);
        return result;
    }

    public Map<String, Object> profile(Long adminId) {
        Admin a = adminMapper.selectById(adminId);
        if (a == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", a.getId());
        result.put("username", a.getUsername());
        result.put("realName", a.getRealName());
        result.put("role", a.getRole());
        result.put("lastLoginTime", a.getLastLoginTime());
        return result;
    }

    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            redis.delete(Constants.KEY_ADMIN_TOKEN + token);
        }
    }

    public String operatorName(Long adminId) {
        Admin a = adminMapper.selectById(adminId);
        return a == null ? "未知" : (StringUtils.hasText(a.getRealName()) ? a.getRealName() : a.getUsername());
    }
}
