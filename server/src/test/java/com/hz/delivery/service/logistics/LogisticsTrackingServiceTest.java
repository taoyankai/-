package com.hz.delivery.service.logistics;

import com.hz.delivery.common.Constants;
import com.hz.delivery.common.BizException;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.entity.Order;
import com.hz.delivery.mapper.OrderMapper;
import com.hz.delivery.mapper.OrderTrackMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
