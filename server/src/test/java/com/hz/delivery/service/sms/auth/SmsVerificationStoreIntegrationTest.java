package com.hz.delivery.service.sms.auth;

import com.hz.delivery.config.BizProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 仅连接 TEST_REDIS_HOST 指向的临时 Redis；不连接验证/正式系统的数据卷。 */
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_HOST", matches = ".+")
class SmsVerificationStoreIntegrationTest {
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private SmsVerificationStore store;
    private String phone;

    @BeforeAll static void connect() {
        factory = new LettuceConnectionFactory(System.getenv("TEST_REDIS_HOST"), 6379);
        factory.afterPropertiesSet(); factory.start();
        redis = new StringRedisTemplate(factory); redis.afterPropertiesSet();
    }
    @AfterAll static void disconnect() { factory.destroy(); }
    @BeforeEach void setup() {
        store = new SmsVerificationStore(redis);
        phone = "test-" + UUID.randomUUID();
    }
    @AfterEach void cleanup() {
        redis.delete("hz:sms:auth:lock:" + phone);
        redis.delete("hz:sms:auth:session:integration:" + phone);
        redis.delete("hz:sms:auth:session:aliyun-pnvs:hz-client-login:" + phone);
    }

    @Test void realRedisConsumesExactlyOnceAndExpiresSession() throws Exception {
        store.withLock("integration", phone, session -> { session.save("nonce", 30); return null; });
        assertEquals(Boolean.TRUE, store.withLock("integration", phone, session -> session.consume("nonce")));
        assertEquals(Boolean.FALSE, store.withLock("integration", phone, session -> session.consume("nonce")));
        store.withLock("integration", phone, session -> { session.save("short-lived", 1); return null; });
        Thread.sleep(1150);
        assertNull(store.withLock("integration", phone, SmsVerificationStore.Session::get));
    }

    @Test void lostLeaseCannotSaveConsumeOrReleaseSuccessorLock() {
        String lock = "hz:sms:auth:lock:" + phone;
        store.withLock("integration", phone, session -> {
            session.save("nonce", 30);
            redis.opsForValue().set(lock, "new-owner", Duration.ofSeconds(30));
            assertThrows(SmsVerificationException.class, () -> session.save("stale-write", 30));
            assertFalse(session.consume("nonce"));
            return null;
        });
        assertEquals("new-owner", redis.opsForValue().get(lock));
        assertEquals("nonce", redis.opsForValue().get("hz:sms:auth:session:integration:" + phone));
    }

    @Test void busyLockRejectsWithoutRunningCallback() {
        redis.opsForValue().set("hz:sms:auth:lock:" + phone, "other", Duration.ofSeconds(30));
        assertThrows(SmsVerificationException.class, () -> store.withLock("integration", phone,
                session -> { fail("busy callback must not run"); return null; }));
    }

    @Test void parallelHostedVerificationOnlyAuthenticatesOnce() throws Exception {
        var client = mock(AliyunPnvsClient.class);
        var service = new AliyunSmsVerificationService(client, store, new BizProperties());
        service.sendCode(phone);
        when(client.verify(eq(phone), eq("123456"), anyString())).thenAnswer(invocation -> {
            Thread.sleep(60); return true;
        });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new java.util.ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 24; i++) tasks.add(() -> {
                try { return service.verifyCode(phone, "123456"); }
                catch (SmsVerificationException busy) { return false; }
            });
            long successes = 0;
            for (Future<Boolean> result : pool.invokeAll(tasks)) if (result.get()) successes++;
            assertEquals(1, successes);
            verify(client, times(1)).verify(anyString(), anyString(), anyString());
        } finally { pool.shutdownNow(); }
    }
}
