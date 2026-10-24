package com.hz.delivery.service.logistics;

import com.hz.delivery.common.Constants;
import com.hz.delivery.common.BizException;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.entity.Order;
import com.hz.delivery.mapper.OrderMapper;
import com.hz.delivery.mapper.OrderTrackMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogisticsTrackingServiceTest {

    @Test
    void mapsKuaidi100StatesToOrderStates() {
        assertEquals(Constants.ORDER_SHIPPED,
                LogisticsTrackingService.mapOrderStatus("1", Constants.ORDER_SHIPPED));
        assertEquals(Constants.ORDER_TRANSIT,
                LogisticsTrackingService.mapOrderStatus("0", Constants.ORDER_SHIPPED));
        assertEquals(Constants.ORDER_DELIVERING,
                LogisticsTrackingService.mapOrderStatus("5", Constants.ORDER_TRANSIT));
        assertEquals(Constants.ORDER_SIGNED,
                LogisticsTrackingService.mapOrderStatus("3", Constants.ORDER_DELIVERING));
        assertEquals(Constants.ORDER_EXCEPTION,
                LogisticsTrackingService.mapOrderStatus("2", Constants.ORDER_TRANSIT));
        assertEquals(Constants.ORDER_RETURNED,
                LogisticsTrackingService.mapOrderStatus("14", Constants.ORDER_DELIVERING));
        assertEquals(Constants.ORDER_SIGNED,
                LogisticsTrackingService.mapOrderStatus("304", Constants.ORDER_TRANSIT));
        assertEquals(Constants.ORDER_DELIVERING,
                LogisticsTrackingService.mapOrderStatus("501", Constants.ORDER_TRANSIT));
        assertEquals(Constants.ORDER_SHIPPED,
                LogisticsTrackingService.mapOrderStatus("103", Constants.ORDER_SHIPPED));
        assertEquals(Constants.ORDER_TRANSIT,
                LogisticsTrackingService.mapOrderStatus("1002", Constants.ORDER_SHIPPED));
        assertEquals(Constants.ORDER_EXCEPTION,
                LogisticsTrackingService.mapOrderStatus("204", Constants.ORDER_TRANSIT));
        assertEquals(Constants.ORDER_TRANSIT,
                LogisticsTrackingService.mapOrderStatus("12", Constants.ORDER_SHIPPED));
        assertEquals(Constants.ORDER_RETURNED,
                LogisticsTrackingService.mapOrderStatus("401", Constants.ORDER_DELIVERING));
    }

    @Test
    void staleCarrierStateDoesNotRegressDeliveryProgress() {
        assertEquals(Constants.ORDER_DELIVERING,
                LogisticsTrackingService.mapOrderStatus("0", Constants.ORDER_DELIVERING));
        assertEquals(Constants.ORDER_TRANSIT,
                LogisticsTrackingService.mapOrderStatus("1", Constants.ORDER_TRANSIT));
    }

    @Test
    void aShipmentCanRecoverFromException() {
        assertEquals(Constants.ORDER_TRANSIT,
                LogisticsTrackingService.mapOrderStatus("0", Constants.ORDER_EXCEPTION));
    }

    @Test
    void rejectsWaybillWhenProviderReturnsAnotherCarrier() {
        LogisticsProvider provider = mock(LogisticsProvider.class);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.providerCode("ZTO")).thenReturn("zhongtong");
        when(provider.query(org.mockito.ArgumentMatchers.any())).thenReturn(
                new LogisticsQueryResult(true, "ok", "0", "shunfeng", "123456789",
                        Collections.emptyList()));
        LogisticsTrackingService service = service(provider);

        assertThrows(BizException.class,
                () -> service.validateForShipment(new Order(), "ZTO", "123456789"));
    }

    @Test
    void acceptsMatchingCarrierAndReusesProviderResult() {
        LogisticsProvider provider = mock(LogisticsProvider.class);
        LogisticsQueryResult expected = new LogisticsQueryResult(true, "ok", "0",
                "shentong", "777443291281761", Collections.emptyList());
        when(provider.isEnabled()).thenReturn(true);
        when(provider.providerCode("STO")).thenReturn("shentong");
        when(provider.query(org.mockito.ArgumentMatchers.any())).thenReturn(expected);

        assertSame(expected, service(provider).validateForShipment(
                new Order(), "STO", "777443291281761"));
    }

    private LogisticsTrackingService service(LogisticsProvider provider) {
        BizProperties props = new BizProperties();
        props.getLogistics().setValidateOnShip(true);
        return new LogisticsTrackingService(mock(OrderMapper.class), mock(OrderTrackMapper.class),
                mock(StringRedisTemplate.class), provider, props);
    }

    @Test
    void demoMarkersSkipAccessManualBatchAndScheduledSyncWithoutQueryOrRedis() {
        OrderMapper orders = mock(OrderMapper.class);
        OrderTrackMapper tracks = mock(OrderTrackMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LogisticsProvider provider = mock(LogisticsProvider.class);
        when(provider.isEnabled()).thenReturn(true);
        LogisticsTrackingService service = new LogisticsTrackingService(orders, tracks, redis,
                provider, new BizProperties());
        for (int marker = 0; marker < 3; marker++) {
            Order order = shippedOrder();
            if (marker == 0) order.setOrderNo(" demo202610230001 ");
            if (marker == 1) order.setEmpNo("DEMO-10000");
            if (marker == 2) order.setWaybillNo("demo-SF123456");
            when(orders.selectById(1L)).thenReturn(order);
            assertTrue(service.syncOnAccess(order).isSkipped());
            assertTrue(service.syncByOrderId(1L).isSkipped());
            assertEquals(1, service.syncBatch(List.of(1L)).get("skippedCount"));
            // 即使查询层返回演示记录，服务层仍须阻断。
            when(orders.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(order));
            service.syncInTransitOrders();
            assertTrue(service.applyValidatedResult(order, LogisticsQueryResult.failed("unused")).isSkipped());
        }
        verify(provider, org.mockito.Mockito.never()).query(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(redis, tracks);
    }

    @Test
    void demoShipmentIsRejectedEvenWhenShipmentValidationIsDisabled() {
        LogisticsProvider provider = mock(LogisticsProvider.class);
        BizProperties props = new BizProperties();
        props.getLogistics().setValidateOnShip(false);
        LogisticsTrackingService service = new LogisticsTrackingService(mock(OrderMapper.class),
                mock(OrderTrackMapper.class), mock(StringRedisTemplate.class), provider, props);
        Order order = shippedOrder();
        order.setOrderNo("DEMO202610230001");
        assertThrows(BizException.class, () -> service.validateForShipment(order, "SF", "123456789"));
        assertThrows(BizException.class, () -> service.validateForShipment(shippedOrder(), "SF", " demo-SF123 "));
        verifyNoInteractions(provider);
    }

    @Test
    @SuppressWarnings("unchecked")
    void realOrderStillQueriesProviderWithThirtyMinuteLimit() {
        Order order = shippedOrder();
        LogisticsProvider provider = mock(LogisticsProvider.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(provider.isEnabled()).thenReturn(true);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("hz:logistics:query:SF:123456789", "1", 30, TimeUnit.MINUTES)).thenReturn(true);
        when(provider.query(order)).thenReturn(LogisticsQueryResult.failed("test response"));
        LogisticsTrackingService service = new LogisticsTrackingService(mock(OrderMapper.class),
                mock(OrderTrackMapper.class), redis, provider, new BizProperties());
        assertEquals("test response", service.sync(order).getMessage());
        verify(provider).query(order);
        verify(values).setIfAbsent("hz:logistics:query:SF:123456789", "1", 30, TimeUnit.MINUTES);
    }

    @Test
    void providerRejectsDemoBeforeOpeningHttpConnection() {
        Kuaidi100LogisticsProvider provider = new Kuaidi100LogisticsProvider(new BizProperties(), new ObjectMapper());
        Order order = shippedOrder();
        order.setWaybillNo("DEMO-SF123456");
        assertEquals("演示订单或演示运单禁止查询真实物流平台", provider.query(order).getMessage());
    }

    @Test
    void carrierSignatureUsesConditionalUpdateAndLatestTraceTime() {
        var orders = mock(OrderMapper.class);
        var tracks = mock(OrderTrackMapper.class);
        var order = shippedOrder(); order.setStatus(Constants.ORDER_DELIVERING);
        var time = java.time.LocalDateTime.of(2026, 10, 24, 10, 0);
        when(orders.updateLogisticsStatus(1L, 30, 40, time, "SF", "123456789")).thenReturn(1);
        var service = new LogisticsTrackingService(orders, tracks, mock(StringRedisTemplate.class),
                mock(LogisticsProvider.class), new BizProperties());
        var response = new LogisticsQueryResult(true, "ok", "3", "shunfeng", "123456789",
                List.of(new LogisticsQueryResult.Trace("已签收", time, "签收", "304")));
        assertEquals(40, service.applyValidatedResult(order, response).getStatus());
        assertEquals(time, order.getSignTime());
        verify(orders).updateLogisticsStatus(1L, 30, 40, time, "SF", "123456789");
        verify(orders, org.mockito.Mockito.never()).updateById(org.mockito.ArgumentMatchers.any(Order.class));
    }

    @Test
    void staleCarrierResponseCannotOverwriteReceiptConfirmedWhileQueryWasRunning() {
        var orders = mock(OrderMapper.class);
        var order = shippedOrder();
        var signed = shippedOrder(); signed.setStatus(40);
        when(orders.selectById(1L)).thenReturn(signed);
        var service = new LogisticsTrackingService(orders, mock(OrderTrackMapper.class),
                mock(StringRedisTemplate.class), mock(LogisticsProvider.class), new BizProperties());
        var response = new LogisticsQueryResult(true, "ok", "0", "shunfeng", "123456789", List.of());
        assertEquals(40, service.applyValidatedResult(order, response).getStatus());
        verify(orders).updateLogisticsStatus(1L, 10, 20, null, "SF", "123456789");
        verify(orders, org.mockito.Mockito.never()).updateById(org.mockito.ArgumentMatchers.any(Order.class));
    }

    @Test
    void yundaQuerySendsMappedCarrierAndParsesTraceFromHttpResponse() throws Exception {
        var received = new java.util.concurrent.atomic.AtomicReference<String>();
        var http = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/query", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = ("{\"status\":\"200\",\"message\":\"ok\",\"com\":\"yunda\","
                    + "\"nu\":\"123456789\",\"state\":\"3\",\"data\":[{\"time\":\"2026-10-23 08:00:00\","
                    + "\"context\":\"测试签收节点\",\"statusCode\":\"304\"}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        http.start();
        try {
            BizProperties props = new BizProperties();
            props.getLogistics().setProvider("kuaidi100");
            var cfg = props.getLogistics().getKuaidi100();
            cfg.setKey("unit-test-key");
            cfg.setCustomer("unit-test-customer");
            cfg.setBaseUrl("http://127.0.0.1:" + http.getAddress().getPort() + "/query");
            var provider = new Kuaidi100LogisticsProvider(props, new ObjectMapper());
            Order order = shippedOrder();
            order.setCarrier("YD");
            LogisticsQueryResult result = provider.query(order);
            assertTrue(result.isSuccess());
            assertEquals("yunda", result.getCompanyCode());
            assertEquals("123456789", result.getWaybillNo());
            assertEquals("3", result.getState());
            assertEquals(1, result.getTraces().size());
            assertEquals("304", result.getTraces().get(0).getStatusCode());
            String form = java.net.URLDecoder.decode(received.get(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(form.contains("\"com\":\"yunda\""));
            assertTrue(form.contains("customer=unit-test-customer"));
        } finally {
            http.stop(0);
        }
    }

    private Order shippedOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("HZ202610230001");
        order.setEmpNo("10000");
        order.setCarrier("SF");
        order.setWaybillNo("123456789");
        order.setStatus(Constants.ORDER_SHIPPED);
        return order;
    }
}
