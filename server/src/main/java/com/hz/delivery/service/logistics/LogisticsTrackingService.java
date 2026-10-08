package com.hz.delivery.service.logistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hz.delivery.common.Constants;
import com.hz.delivery.common.BizException;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.entity.Order;
import com.hz.delivery.entity.OrderTrack;
import com.hz.delivery.mapper.OrderMapper;
import com.hz.delivery.mapper.OrderTrackMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 真实物流同步：限频调用供应商、轨迹去重落库，并把承运商状态同步到订单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsTrackingService {

    private static final String LOCK_PREFIX = "hz:logistics:query:";

    private final OrderMapper orderMapper;
    private final OrderTrackMapper trackMapper;
    private final StringRedisTemplate redis;
    private final LogisticsProvider provider;
    private final BizProperties props;

    /** 用户主动查询时调用；未启用通道或命中限频时安静返回现有轨迹。 */
    public SyncResult syncOnAccess(Order order) {
        if (!props.getLogistics().isQueryOnAccess()) {
            return SyncResult.skipped("已关闭查询时即时刷新");
        }
        return sync(order);
    }

    public SyncResult syncByOrderId(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return SyncResult.failed("订单不存在");
        }
        return sync(order);
    }

    /**
     * 发货前用真实查询校验承运商和运单号。查询结果会在发货成功后直接复用，
     * 避免“校验一次、拉轨迹再查一次”造成重复请求。
     */
    public LogisticsQueryResult validateForShipment(Order source, String carrierCode, String waybillNo) {
        if (!props.getLogistics().isValidateOnShip()) {
            return null;
        }
        if (!provider.isEnabled()) {
            throw BizException.of(ErrorCode.WAYBILL_VERIFY_FAILED,
                    "真实物流校验通道未启用，请检查快递100配置后再发货");
        }
        String expectedCompany = provider.providerCode(carrierCode);
        if (!StringUtils.hasText(expectedCompany)) {
            throw BizException.of(ErrorCode.WAYBILL_VERIFY_FAILED, "该承运商暂不支持真实运单校验");
        }

        Order candidate = new Order();
        candidate.setCarrier(carrierCode);
        candidate.setWaybillNo(waybillNo);
        candidate.setPhone(source.getPhone());
        candidate.setProvince(source.getProvince());
        candidate.setCity(source.getCity());
        candidate.setDistrict(source.getDistrict());
        LogisticsQueryResult result = provider.query(candidate);
        if (!result.isSuccess()) {
            throw BizException.of(ErrorCode.WAYBILL_VERIFY_FAILED,
                    "运单校验失败：" + safe(result.getMessage()));
        }
        if (!StringUtils.hasText(result.getCompanyCode())
                || !expectedCompany.equalsIgnoreCase(result.getCompanyCode())) {
            throw BizException.of(ErrorCode.CARRIER_WAYBILL_MISMATCH,
                    "运单号与所选承运商不匹配，请核对后重试");
        }
        if (StringUtils.hasText(result.getWaybillNo())
                && !waybillNo.equalsIgnoreCase(result.getWaybillNo().trim())) {
            throw BizException.of(ErrorCode.WAYBILL_VERIFY_FAILED, "物流平台返回的运单号与录入值不一致");
        }
        return result;
    }

    /** 发货落库后复用预校验结果，立即生成首批真实轨迹。 */
    public SyncResult applyValidatedResult(Order order, LogisticsQueryResult result) {
        if (result == null) {
            return SyncResult.skipped("未启用发货前真实校验");
        }
        rememberQuery(order);
        return persist(order, result);
    }

    public SyncResult sync(Order order) {
        if (!provider.isEnabled()) {
            return SyncResult.skipped("真实物流通道未启用或密钥未配置");
        }
        if (order == null || !StringUtils.hasText(order.getWaybillNo())
                || order.getStatus() == null || order.getStatus() < Constants.ORDER_SHIPPED
                || order.getStatus() == Constants.ORDER_CANCELED) {
            return SyncResult.skipped("订单尚未绑定可查询的真实运单");
        }
        if (order.getStatus() == Constants.ORDER_SIGNED || order.getStatus() == Constants.ORDER_RETURNED) {
            return SyncResult.skipped("运单已结束，无需继续同步");
        }

        int interval = Math.max(30, props.getLogistics().getMinIntervalMinutes());
        String lockKey = LOCK_PREFIX + order.getCarrier() + ":" + order.getWaybillNo();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, "1", interval, TimeUnit.MINUTES);
            if (!Boolean.TRUE.equals(acquired)) {
                return SyncResult.skipped("查询过于频繁，请稍后再试");
            }
        } catch (Exception e) {
            // Redis 短暂不可用时宁可跳过，避免失去限频保护后重复产生查询费用。
            log.warn("物流查询限频检查失败 waybill={} error={}", order.getWaybillNo(), e.getMessage());
            return SyncResult.skipped("限频服务暂不可用，请稍后再试");
        }

        LogisticsQueryResult response = provider.query(order);
        if (!response.isSuccess()) {
            log.warn("真实物流查询未成功 orderNo={} waybill={} message={}",
                    order.getOrderNo(), order.getWaybillNo(), response.getMessage());
            return SyncResult.failed(response.getMessage());
        }

        return persist(order, response);
    }

    private SyncResult persist(Order order, LogisticsQueryResult response) {
        Set<String> existing = new HashSet<>();
        List<OrderTrack> oldTracks = trackMapper.selectList(new LambdaQueryWrapper<OrderTrack>()
                .eq(OrderTrack::getOrderId, order.getId())
                .eq(OrderTrack::getSource, "carrier"));
        for (OrderTrack old : oldTracks) {
            existing.add(traceKey(old.getTrackTime(), old.getDescription()));
        }

        int inserted = 0;
        for (LogisticsQueryResult.Trace trace : response.getTraces()) {
            if (!existing.add(traceKey(trace.getTime(), trace.getDescription()))) {
                continue;
            }
            OrderTrack item = new OrderTrack();
            item.setOrderId(order.getId());
            item.setOrderNo(order.getOrderNo());
            item.setStatus(mapTraceStatus(trace));
            item.setDescription(trace.getDescription());
            item.setTrackTime(trace.getTime());
            item.setSource("carrier");
            trackMapper.insert(item);
            inserted++;
        }

        int newStatus = mapOrderStatus(response.getState(), order.getStatus());
        if (newStatus != order.getStatus()) {
            order.setStatus(newStatus);
            if (newStatus == Constants.ORDER_SIGNED && order.getSignTime() == null) {
                order.setSignTime(latestTraceTime(response.getTraces()));
                if (order.getSignTime() == null) {
                    order.setSignTime(LocalDateTime.now());
                }
            }
            orderMapper.updateById(order);
        }
        log.info("真实物流同步完成 orderNo={} waybill={} 新增轨迹={} 状态={}",
                order.getOrderNo(), order.getWaybillNo(), inserted, newStatus);
        return SyncResult.success(inserted, newStatus);
    }

    public Map<String, Object> syncBatch(List<Long> orderIds) {
        Map<String, Object> result = new LinkedHashMap<>();
        int success = 0;
        int skipped = 0;
        int failed = 0;
        int inserted = 0;
        if (orderIds != null) {
            for (Long id : orderIds.stream().filter(java.util.Objects::nonNull).distinct().limit(50).toList()) {
                try {
                    SyncResult one = syncByOrderId(id);
                    inserted += one.getInsertedCount();
                    if (!one.isSuccess()) failed++;
                    else if (one.isSkipped()) skipped++;
                    else success++;
                } catch (Exception e) {
                    failed++;
                    log.error("批量同步物流失败 orderId={}", id, e);
                }
            }
        }
        result.put("successCount", success);
        result.put("skippedCount", skipped);
        result.put("failedCount", failed);
        result.put("insertedCount", inserted);
        return result;
    }

    /** 每 30 分钟拉取一批在途运单；Redis 锁确保多实例部署也不会重复查询。 */
    @Scheduled(cron = "${hz.delivery.logistics.sync-cron:0 */30 * * * ?}")
    public void syncInTransitOrders() {
        if (!provider.isEnabled()) {
            return;
        }
        int size = Math.max(1, Math.min(props.getLogistics().getBatchSize(), 500));
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, Constants.ORDER_SHIPPED, Constants.ORDER_TRANSIT,
                        Constants.ORDER_DELIVERING, Constants.ORDER_EXCEPTION)
                .isNotNull(Order::getWaybillNo)
                .orderByAsc(Order::getUpdateTime)
                .last("LIMIT " + size));
        for (Order order : orders) {
            try {
                sync(order);
            } catch (Exception e) {
                log.error("定时同步物流失败 orderNo={}", order.getOrderNo(), e);
            }
        }
    }

    static int mapOrderStatus(String state, int currentStatus) {
        if (!StringUtils.hasText(state)) {
            return currentStatus;
        }
        return switch (state) {
            case "1", "101", "102", "103" -> currentStatus == Constants.ORDER_TRANSIT
                    || currentStatus == Constants.ORDER_DELIVERING
                    ? currentStatus : Constants.ORDER_SHIPPED;
            case "3", "301", "302", "303", "304" -> Constants.ORDER_SIGNED;
            case "5", "501" -> Constants.ORDER_DELIVERING;
            case "2", "201", "202", "203", "204", "205", "206", "207", "208", "209", "210", "13"
                    -> Constants.ORDER_EXCEPTION;
            case "4", "6", "14", "401" -> Constants.ORDER_RETURNED;
            case "0", "7", "8", "10", "11", "12", "1001", "1002", "1003"
                    -> currentStatus == Constants.ORDER_DELIVERING ? Constants.ORDER_DELIVERING : Constants.ORDER_TRANSIT;
            default -> currentStatus == Constants.ORDER_DELIVERING
                    ? Constants.ORDER_DELIVERING : Constants.ORDER_TRANSIT;
        };
    }

    private static int mapTraceStatus(LogisticsQueryResult.Trace trace) {
        if (StringUtils.hasText(trace.getStatusCode())) {
            return mapOrderStatus(trace.getStatusCode(), Constants.ORDER_TRANSIT);
        }
        String text = safe(trace.getStatusName()) + " " + safe(trace.getDescription());
        if (text.contains("签收") && !text.contains("退签")) return Constants.ORDER_SIGNED;
        if (text.contains("派件") || text.contains("派送")) return Constants.ORDER_DELIVERING;
        if (text.contains("退回") || text.contains("退签") || text.contains("拒签")) return Constants.ORDER_RETURNED;
        if (text.contains("异常") || text.contains("疑难") || text.contains("滞留")) return Constants.ORDER_EXCEPTION;
        if (text.contains("揽收") || text.contains("收件")) return Constants.ORDER_SHIPPED;
        return Constants.ORDER_TRANSIT;
    }

    private static LocalDateTime latestTraceTime(List<LogisticsQueryResult.Trace> traces) {
        return traces.stream().map(LogisticsQueryResult.Trace::getTime)
                .filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
    }

    private static String traceKey(LocalDateTime time, String description) {
        return String.valueOf(time) + "|" + safe(description);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void rememberQuery(Order order) {
        int interval = Math.max(30, props.getLogistics().getMinIntervalMinutes());
        try {
            redis.opsForValue().set(LOCK_PREFIX + order.getCarrier() + ":" + order.getWaybillNo(),
                    "1", interval, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("记录物流查询限频失败 waybill={} error={}", order.getWaybillNo(), e.getMessage());
        }
    }

    @Data
    @AllArgsConstructor
    public static class SyncResult {
        private boolean success;
        private boolean skipped;
        private String message;
        private int insertedCount;
        private Integer status;

        static SyncResult success(int inserted, int status) {
            return new SyncResult(true, false, "同步成功", inserted, status);
        }

        static SyncResult skipped(String message) {
            return new SyncResult(true, true, message, 0, null);
        }

        static SyncResult failed(String message) {
            return new SyncResult(false, false, message, 0, null);
        }
    }
}
