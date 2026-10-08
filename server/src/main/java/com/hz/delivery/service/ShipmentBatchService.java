package com.hz.delivery.service;

import com.hz.delivery.dto.BatchShipDTO;
import com.hz.delivery.dto.BatchShipItemDTO;
import com.hz.delivery.dto.ShipDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多承运商批量发货编排。每一笔通过 ShipmentService 的事务代理独立提交，
 * 因而单笔失败不会回滚已经成功的其它订单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentBatchService {

    private final ShipmentService shipmentService;

    public Map<String, Object> ship(BatchShipDTO dto, String operator) {
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;

        for (BatchShipItemDTO item : dto.getItems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", item.getOrderId());
            row.put("carrierCode", normalize(item.getCarrierCode()));
            row.put("waybillNo", trim(item.getWaybillNo()));
            try {
                ShipDTO one = new ShipDTO();
                one.setOrderIds(List.of(item.getOrderId()));
                one.setCarrier(normalize(item.getCarrierCode()));
                one.setWaybillNo(trim(item.getWaybillNo()));
                Map<String, Object> oneResult = shipmentService.ship(one, operator);
                boolean ok = number(oneResult.get("successCount")) > 0;
                row.put("success", ok);
                if (ok) {
                    row.put("message", "发货成功");
                    success++;
                } else {
                    row.put("message", firstFailure(oneResult));
                }
            } catch (Exception e) {
                row.put("success", false);
                row.put("message", e.getMessage() == null ? "发货失败" : e.getMessage());
                log.warn("批量发货单笔失败 orderId={} error={}", item.getOrderId(), e.getMessage());
            }
            results.add(row);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", results.size());
        response.put("successCount", success);
        response.put("failCount", results.size() - success);
        response.put("results", results);
        return response;
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static String firstFailure(Map<String, Object> result) {
        Object failures = result.get("failList");
        if (failures instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.get(0));
        }
        return "发货失败，请刷新后重试";
    }

    private static String normalize(String value) {
        return trim(value).toUpperCase(java.util.Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
