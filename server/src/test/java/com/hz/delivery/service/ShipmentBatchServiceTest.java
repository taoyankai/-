package com.hz.delivery.service;

import com.hz.delivery.dto.BatchShipDTO;
import com.hz.delivery.dto.BatchShipItemDTO;
import com.hz.delivery.dto.ShipDTO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShipmentBatchServiceTest {

    @Test
    void supportsDifferentCarriersAndKeepsSingleFailureIsolated() {
        ShipmentService shipmentService = mock(ShipmentService.class);
        when(shipmentService.ship(any(ShipDTO.class), eq("tester"))).thenAnswer(invocation -> {
            ShipDTO item = invocation.getArgument(0);
            Map<String, Object> result = new LinkedHashMap<>();
            if ("ZTO".equals(item.getCarrier())) {
                result.put("successCount", 1);
                result.put("failList", List.of());
            } else {
                result.put("successCount", 0);
                result.put("failList", List.of("运单号与所选承运商不匹配"));
            }
            return result;
        });

        BatchShipDTO request = new BatchShipDTO();
        request.setItems(List.of(item(1L, "zto", "ZTO001"), item(2L, "sf", "SF002")));
        Map<String, Object> response = new ShipmentBatchService(shipmentService).ship(request, "tester");

        assertEquals(1, response.get("successCount"));
        assertEquals(1, response.get("failCount"));
        List<?> rows = (List<?>) response.get("results");
        assertTrue((Boolean) ((Map<?, ?>) rows.get(0)).get("success"));
        assertFalse((Boolean) ((Map<?, ?>) rows.get(1)).get("success"));
        assertEquals("ZTO", ((Map<?, ?>) rows.get(0)).get("carrierCode"));
    }

    private static BatchShipItemDTO item(Long orderId, String carrier, String waybill) {
        BatchShipItemDTO item = new BatchShipItemDTO();
        item.setOrderId(orderId);
        item.setCarrierCode(carrier);
        item.setWaybillNo(waybill);
        return item;
    }
}
