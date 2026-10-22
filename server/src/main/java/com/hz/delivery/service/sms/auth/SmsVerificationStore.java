package com.hz.delivery.service.sms.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Redis 互斥发送/核验；所有写入检查锁所有权，防止锁过期后的旧请求覆盖新会话。 */
@Component
@RequiredArgsConstructor
public class SmsVerificationStore {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> DELETE_MATCH = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) ~= ARGV[1] then return 0 end " +
            "redis.call('set',KEYS[2],ARGV[2],'EX',ARGV[3]); return 1", Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) ~= ARGV[1] then return 0 end " +
            "if redis.call('get',KEYS[2]) ~= ARGV[2] then return 0 end return redis.call('del',KEYS[2])", Long.class);

    public interface Session {
        String get();
        void save(String value, long ttl);
        boolean consume(String expected);
    }

    public <T> T withLock(String scope, String phone, Function<Session, T> action) {
        String lockKey = "hz:sms:auth:lock:" + phone;
        String sessionKey = "hz:sms:auth:session:" + scope + ":" + phone;
        String owner = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, owner, Duration.ofSeconds(120)))) {
            throw new SmsVerificationException(false);
        }
        try {
            return action.apply(new Session() {
                public String get() { return redis.opsForValue().get(sessionKey); }
                public void save(String value, long ttl) {
                    if (!Long.valueOf(1).equals(redis.execute(SAVE, List.of(lockKey, sessionKey),
                            owner, value, String.valueOf(ttl)))) {
                        throw new SmsVerificationException(true);
                    }
                }
                public boolean consume(String expected) {
                    return Long.valueOf(1).equals(redis.execute(CONSUME, List.of(lockKey, sessionKey), owner, expected));
                }
            });
        } finally {
            redis.execute(DELETE_MATCH, List.of(lockKey), owner);
        }
    }
}
