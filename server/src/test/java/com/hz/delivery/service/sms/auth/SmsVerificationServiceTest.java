package com.hz.delivery.service.sms.auth;

import com.hz.delivery.config.BizProperties;
import com.hz.delivery.service.sms.SmsSender;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmsVerificationServiceTest {
    private final BizProperties props = new BizProperties();
    private final MemoryStore store = new MemoryStore();
    private final AliyunPnvsClient client = mock(AliyunPnvsClient.class);
    private final AliyunSmsVerificationService service = new AliyunSmsVerificationService(client, store, props);
    private static final String PHONE = "19900000001";

    @Test
    void hostedSessionHasNoCodeAndCanOnlyBeConsumedOnce() {
        var sent = service.sendCode(PHONE);
        assertNull(sent.mockCode());
        assertNull(service.mockCodeHint());
        assertEquals(32, store.data.values().iterator().next().length());
        when(client.verify(eq(PHONE), eq("123456"), anyString())).thenReturn(true);
        assertTrue(service.verifyCode(PHONE, "123456"));
        assertFalse(service.verifyCode(PHONE, "123456"));
        verify(client, times(1)).verify(anyString(), anyString(), anyString());
    }

    @Test
    void invalidCodeRetainsSessionButExpiredSessionNeverCallsCloud() {
        service.sendCode(PHONE);
        when(client.verify(anyString(), anyString(), anyString())).thenReturn(false);
        assertFalse(service.verifyCode(PHONE, "999999"));
        assertEquals(1, store.data.size());
        store.data.clear(); // 模拟 Redis TTL 到期
        assertFalse(service.verifyCode(PHONE, "123456"));
        verify(client, times(1)).verify(anyString(), anyString(), anyString());
    }

    @Test
    void cloudOutageDoesNotConsumeSessionAndCannotAuthenticate() {
        service.sendCode(PHONE);
        when(client.verify(anyString(), anyString(), anyString())).thenThrow(new SmsVerificationException(false));
        assertThrows(SmsVerificationException.class, () -> service.verifyCode(PHONE, "123456"));
        assertEquals(1, store.data.size());
    }

    @Test
    void ambiguousSendKeepsAttemptButDefiniteRejectionPreservesPreviousSession() {
        service.sendCode(PHONE);
        String previous = store.data.values().iterator().next();
        doThrow(new SmsVerificationException(false)).when(client).send(anyString(), anyString());
        assertThrows(SmsVerificationException.class, () -> service.sendCode(PHONE));
        assertEquals(previous, store.data.values().iterator().next());
        doThrow(new SmsVerificationException(true)).when(client).send(anyString(), anyString());
        assertThrows(SmsVerificationException.class, () -> service.sendCode(PHONE));
        assertNotEquals(previous, store.data.values().iterator().next());
    }

    @Test
    void concurrentSubmissionsOnlyProduceOneSuccess() throws Exception {
        service.sendCode(PHONE);
        when(client.verify(anyString(), anyString(), anyString())).thenReturn(true);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new java.util.ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 20; i++) tasks.add(() -> service.verifyCode(PHONE, "123456"));
            long successes = 0;
            for (Future<Boolean> result : pool.invokeAll(tasks)) if (result.get()) successes++;
            assertEquals(1, successes);
            verify(client, times(1)).verify(anyString(), anyString(), anyString());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void localMockStillWorksAndOtherChannelsIgnoreFixedCode() {
        SmsSender sender = mock(SmsSender.class);
        when(sender.provider()).thenReturn("mock");
        var local = new LocalSmsVerificationService(sender, store, props);
        assertEquals("123456", local.sendCode(PHONE).mockCode());
        assertFalse(local.verifyCode(PHONE, "999999"));
        assertTrue(local.verifyCode(PHONE, "123456"));
        assertFalse(local.verifyCode(PHONE, "123456"));
        when(sender.provider()).thenReturn("hbu");
        assertNull(local.mockCodeHint());
    }

    private static class MemoryStore extends SmsVerificationStore {
        final Map<String, String> data = new HashMap<>();
        MemoryStore() { super(null); }
        @Override public synchronized <T> T withLock(String scope, String phone, Function<Session, T> action) {
            String key = scope + phone;
            return action.apply(new Session() {
                public String get() { return data.get(key); }
                public void save(String value, long ttl) { data.put(key, value); }
                public boolean consume(String expected) { return data.remove(key, expected); }
            });
        }
    }
}
