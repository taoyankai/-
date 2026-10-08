package com.hz.delivery.service;

import com.hz.delivery.common.BizException;
import com.hz.delivery.common.Constants;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.common.PasswordUtil;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.entity.Admin;
import com.hz.delivery.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final String FAIL_USER_PREFIX = "hz:login:admin:fail:user:";
    private static final String FAIL_IP_PREFIX = "hz:login:admin:fail:ip:";
    private static final String LOCK_USER_PREFIX = "hz:login:admin:lock:user:";
    private static final String LOCK_IP_PREFIX = "hz:login:admin:lock:ip:";

    private final AdminMapper adminMapper;
    private final StringRedisTemplate redis;
    private final BizProperties props;

    public Map<String, Object> login(String username, String password, String clientIp) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
        String userTag = fingerprint(normalizedUsername);
        String ipTag = fingerprint(StringUtils.hasText(clientIp) ? clientIp.trim() : "unknown");
        if (Boolean.TRUE.equals(redis.hasKey(LOCK_USER_PREFIX + userTag))
                || Boolean.TRUE.equals(redis.hasKey(LOCK_IP_PREFIX + ipTag))) {
            throw BizException.of(ErrorCode.ADMIN_LOGIN_LOCKED,
                    "后台登录失败次数过多，请 " + lockMinutes() + " 分钟后再试");
        }

        Admin a = adminMapper.selectByUsername(normalizedUsername);
        if (a == null || !PasswordUtil.matches(password, a.getPassword())) {
            recordFailure(userTag, ipTag);
            throw BizException.of(ErrorCode.PARAM_ERROR, "账号或密码错误");
        }
        if (a.getStatus() == null || a.getStatus() != 1) {
            recordFailure(userTag, ipTag);
            throw BizException.of(ErrorCode.FORBIDDEN, "账号已停用，请联系管理员");
        }

        clearFailures(userTag, ipTag);

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
        result.put("mustChangePassword", mustChangePassword(a));
        result.put("expiresIn", Constants.ADMIN_TOKEN_TTL_SECONDS);
        log.info("后台登录成功 username={} ip={}", normalizedUsername, clientIp);
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
        result.put("mustChangePassword", mustChangePassword(a));
        result.put("lastLoginTime", a.getLastLoginTime());
        return result;
    }

    public void changePassword(Long adminId, String currentPassword, String newPassword) {
        Admin a = adminMapper.selectById(adminId);
        if (a == null || a.getStatus() == null || a.getStatus() != 1) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        if (!PasswordUtil.matches(currentPassword, a.getPassword())) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "当前密码不正确");
        }
        if (PasswordUtil.matches(newPassword, a.getPassword())) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "新密码不能与当前密码相同");
        }
        if (!strongPassword(newPassword)) {
            throw BizException.of(ErrorCode.PARAM_ERROR,
                    "新密码须为 12-64 位，并至少包含大写字母、小写字母、数字、特殊字符中的三类");
        }
        a.setPassword(PasswordUtil.hash(newPassword));
        a.setMustChangePassword(0);
        adminMapper.updateById(a);
        log.info("后台账号已修改密码 username={}", a.getUsername());
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

    private void recordFailure(String userTag, String ipTag) {
        long userFails = incrementWithExpiry(FAIL_USER_PREFIX + userTag);
        long ipFails = incrementWithExpiry(FAIL_IP_PREFIX + ipTag);
        int maxFail = Math.max(1, props.getAdminLoginMaxFail());
        if (userFails >= maxFail) {
            redis.opsForValue().set(LOCK_USER_PREFIX + userTag, "1",
                    lockMinutes(), TimeUnit.MINUTES);
            redis.delete(FAIL_USER_PREFIX + userTag);
        }
        if (ipFails >= maxFail) {
            redis.opsForValue().set(LOCK_IP_PREFIX + ipTag, "1",
                    lockMinutes(), TimeUnit.MINUTES);
            redis.delete(FAIL_IP_PREFIX + ipTag);
        }
    }

    private long incrementWithExpiry(String key) {
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) {
            redis.expire(key, lockMinutes(), TimeUnit.MINUTES);
        }
        return n == null ? 1L : n;
    }

    private long lockMinutes() {
        return Math.max(1, props.getAdminLoginLockMinutes());
    }

    private void clearFailures(String userTag, String ipTag) {
        redis.delete(FAIL_USER_PREFIX + userTag);
        redis.delete(FAIL_IP_PREFIX + ipTag);
        redis.delete(LOCK_USER_PREFIX + userTag);
        redis.delete(LOCK_IP_PREFIX + ipTag);
    }

    private boolean mustChangePassword(Admin a) {
        return a.getMustChangePassword() == null || a.getMustChangePassword() != 0;
    }

    private boolean strongPassword(String value) {
        if (value == null || value.length() < 12 || value.length() > 64) {
            return false;
        }
        int categories = 0;
        if (value.chars().anyMatch(Character::isLowerCase)) categories++;
        if (value.chars().anyMatch(Character::isUpperCase)) categories++;
        if (value.chars().anyMatch(Character::isDigit)) categories++;
        if (value.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) categories++;
        return categories >= 3;
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("登录限流标识生成失败", e);
        }
    }
}
