package com.hz.delivery.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hz.delivery.common.*;
import com.hz.delivery.dto.ShipDTO;
import com.hz.delivery.entity.Carrier;
import com.hz.delivery.entity.ImportBatch;
import com.hz.delivery.entity.Order;
import com.hz.delivery.entity.OrderTrack;
import com.hz.delivery.excel.WaybillExcelRow;
import com.hz.delivery.mapper.CarrierMapper;
import com.hz.delivery.mapper.OrderMapper;
import com.hz.delivery.mapper.OrderTrackMapper;
import com.hz.delivery.service.logistics.LogisticsQueryResult;
import com.hz.delivery.service.logistics.LogisticsTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 发货与物流服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private static final int MAX_ROWS = 5000;

    private final OrderMapper orderMapper;
    private final OrderTrackMapper trackMapper;
    private final CarrierMapper carrierMapper;
    private final LogisticsTrackingService logisticsTrackingService;

    /* ==================== 订单查询（后台） ==================== */

    public PageResult<Order> pageOrders(long page, long size, Integer status, String org,
                                        String keyword, Integer slaLevel) {
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<Order>().orderByDesc(Order::getCreateTime);
        if (status != null) {
            w.eq(Order::getStatus, status);
        }
        if (StringUtils.hasText(org)) {
            w.eq(Order::getOrg, org);
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(x -> x.like(Order::getOrderNo, k)
                    .or().like(Order::getGranteeName, k)
                    .or().like(Order::getPhone, k)
                    .or().like(Order::getWaybillNo, k)
                    .or().like(Order::getReceiver, k));
        }
        // SLA 维度筛选：仅针对待发货订单
        if (slaLevel != null) {
            w.eq(Order::getStatus, Constants.ORDER_PENDING);
            switch (slaLevel) {
                case 3 -> w.apply("sla_deadline < NOW()");
                case 2 -> w.apply("sla_deadline >= NOW() AND sla_deadline < DATE_ADD(NOW(), INTERVAL 1 DAY)");
                case 1 -> w.apply("sla_deadline >= DATE_ADD(NOW(), INTERVAL 1 DAY) AND sla_deadline < DATE_ADD(NOW(), INTERVAL 3 DAY)");
                default -> { }
            }
        }
        Page<Order> p = orderMapper.selectPage(new Page<>(page, size), w);
        p.getRecords().forEach(this::fillRemain);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    public Order detail(Long id) {
        Order o = orderMapper.selectById(id);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        o.setTraces(trackMapper.selectList(new LambdaQueryWrapper<OrderTrack>()
                .eq(OrderTrack::getOrderId, id)
                .orderByDesc(OrderTrack::getTrackTime)
                .orderByDesc(OrderTrack::getId)));
        fillRemain(o);
        return o;
    }

    public List<Carrier> carriers() {
        return carrierMapper.selectList(new LambdaQueryWrapper<Carrier>()
                .eq(Carrier::getStatus, 1)
                .orderByAsc(Carrier::getSort));
    }

    /* ==================== 发货 ==================== */

    /**
     * 批量发货：逐单独立校验，失败不影响其它订单，返回明细供运营核对。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ship(ShipDTO dto, String operator) {
        Carrier carrier = carrierMapper.selectOne(new LambdaQueryWrapper<Carrier>()
                .eq(Carrier::getCode, dto.getCarrier())
                .eq(Carrier::getStatus, 1)
                .last("LIMIT 1"));
        if (carrier == null) {
            throw BizException.of(ErrorCode.CARRIER_REQUIRED, "承运商不存在：" + dto.getCarrier());
        }

        boolean single = dto.getOrderIds().size() == 1 && StringUtils.hasText(dto.getWaybillNo());
        if (!single) {
            throw BizException.of(ErrorCode.WAYBILL_REQUIRED,
                    "真实物流必须逐单绑定承运商运单号；多笔订单请填写每笔运单号或使用批量导入");
        }
        List<String> fails = new ArrayList<>();
        List<String> successNos = new ArrayList<>();

        for (Long id : dto.getOrderIds()) {
            Order o = orderMapper.selectById(id);
            if (o == null) {
                fails.add("订单 ID " + id + " 不存在");
                continue;
            }
            if (o.getStatus() == null || o.getStatus() != Constants.ORDER_PENDING) {
                fails.add(o.getOrderNo() + "：当前状态为「" + GranteeService.orderStatusName(o.getStatus()) + "」，不可发货");
                continue;
            }
            if (StringUtils.hasText(o.getWaybillNo())) {
                fails.add(o.getOrderNo() + "：已存在运单号，不可重复发货");
                continue;
            }
            if (overdueWithoutReason(o, LocalDateTime.now())) {
                fails.add(delayReasonRequiredMessage(o));
                continue;
            }

            String waybill = dto.getWaybillNo().trim();
            if (!validWaybill(waybill)) {
                fails.add(o.getOrderNo() + "：运单号长度应为 6～32 个字符");
                continue;
            }
            if (waybillExisted(waybill)) {
                fails.add(o.getOrderNo() + "：运单号 " + waybill + " 已被占用");
                continue;
            }

            LogisticsQueryResult verified;
            try {
                verified = logisticsTrackingService.validateForShipment(o, carrier.getCode(), waybill);
            } catch (BizException e) {
                fails.add(o.getOrderNo() + "：" + e.getMessage());
                continue;
            }

            int updated = orderMapper.ship(id, carrier.getCode(), carrier.getName(), waybill);
            if (updated == 0) {
                Order latest = orderMapper.selectById(id);
                fails.add(overdueWithoutReason(latest, LocalDateTime.now())
                        ? delayReasonRequiredMessage(latest)
                        : o.getOrderNo() + "：状态已变更，请刷新后重试");
                continue;
            }
            o.setCarrier(carrier.getCode());
            o.setCarrierName(carrier.getName());
            o.setWaybillNo(waybill);
            o.setStatus(Constants.ORDER_SHIPPED);
            o.setShipTime(LocalDateTime.now());
            addTrack(o, Constants.ORDER_SHIPPED, "商家已绑定「" + carrier.getName()
                    + "」真实运单号 " + waybill + "，正在等待承运商揽收", "system");
            logisticsTrackingService.applyValidatedResult(o, verified);
            successNos.add(o.getOrderNo() + " / " + waybill);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", successNos.size());
        result.put("failCount", fails.size());
        result.put("successList", successNos);
        result.put("failList", fails);
        log.info("发货完成 operator={} 成功={} 失败={}", operator, successNos.size(), fails.size());
        return result;
    }

    /**
     * 撤单退回待发货（仅用于录入错误纠正）
     */
    @Transactional(rollbackFor = Exception.class)
    public void revertShip(Long orderId, String reason) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (o.getStatus() == null || o.getStatus() != Constants.ORDER_SHIPPED) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ERROR, "仅「已发货」状态可撤回");
        }
        o.setStatus(Constants.ORDER_PENDING);
        o.setShipTime(null);
        o.setWaybillNo(null);
        o.setCarrier(null);
        o.setCarrierName(null);
        o.setExceptionReason(reason);
        orderMapper.updateById(o);
        addTrack(o, Constants.ORDER_PENDING, "发货信息已撤回修正：" + (reason == null ? "运营操作" : reason), "manual");
    }

    /* ==================== 运单号批量导入 ==================== */

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importWaybills(MultipartFile file, String operator) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ErrorCode.EXCEL_EMPTY);
        }
        List<WaybillExcelRow> rows;
        try {
            rows = EasyExcel.read(file.getInputStream()).head(WaybillExcelRow.class)
                    .sheet().headRowNumber(1).doReadSync();
        } catch (Exception e) {
            throw BizException.of(ErrorCode.EXCEL_FORMAT_ERROR);
        }
        if (rows == null || rows.isEmpty()) {
            throw BizException.of(ErrorCode.EXCEL_EMPTY);
        }
        if (rows.size() > MAX_ROWS) {
            throw BizException.of(ErrorCode.EXCEL_TOO_LARGE);
        }

        Map<String, String> carrierNameMap = carriers().stream()
                .collect(Collectors.toMap(Carrier::getCode, Carrier::getName, (a, b) -> a));

        List<String> fails = new ArrayList<>();
        Set<String> usedWaybills = new HashSet<>();
        Set<String> handledOrders = new HashSet<>();
        int success = 0;

        for (int i = 0; i < rows.size(); i++) {
            WaybillExcelRow r = rows.get(i);
            int lineNo = i + 2;
            String orderNo = trim(r.getOrderNo());
            String carrierCode = trim(r.getCarrierCode());
            String waybillNo = trim(r.getWaybillNo());

            if (!StringUtils.hasText(orderNo)) {
                fails.add("第 " + lineNo + " 行：订单号为空");
                continue;
            }
            if (!StringUtils.hasText(carrierCode) || !carrierNameMap.containsKey(carrierCode)) {
                fails.add("第 " + lineNo + " 行：承运商编码非法（" + carrierCode + "）");
                continue;
            }
            if (!StringUtils.hasText(waybillNo)) {
                fails.add("第 " + lineNo + " 行：运单号为空");
                continue;
            }
            if (!validWaybill(waybillNo)) {
                fails.add("第 " + lineNo + " 行：运单号长度应为 6～32 个字符");
                continue;
            }
            if (!usedWaybills.add(waybillNo)) {
                fails.add("第 " + lineNo + " 行：运单号 " + waybillNo + " 在文件内重复");
                continue;
            }
            if (!handledOrders.add(orderNo)) {
                fails.add("第 " + lineNo + " 行：订单号 " + orderNo + " 在文件内重复");
                continue;
            }

            Order o = orderMapper.selectByOrderNo(orderNo);
            if (o == null) {
                fails.add("第 " + lineNo + " 行：订单号 " + orderNo + " 不存在");
                continue;
            }
            if (o.getStatus() != null && o.getStatus() != Constants.ORDER_PENDING) {
                fails.add("第 " + lineNo + " 行：订单 " + orderNo + " 已"
                        + GranteeService.orderStatusName(o.getStatus()) + "，不可重复发货");
                continue;
            }
            if (overdueWithoutReason(o, LocalDateTime.now())) {
                fails.add("第 " + lineNo + " 行：" + delayReasonRequiredMessage(o));
                continue;
            }
            if (waybillExisted(waybillNo)) {
                fails.add("第 " + lineNo + " 行：运单号 " + waybillNo + " 已存在");
                continue;
            }

            LogisticsQueryResult verified;
            try {
                verified = logisticsTrackingService.validateForShipment(o, carrierCode, waybillNo);
            } catch (BizException e) {
                fails.add("第 " + lineNo + " 行：" + e.getMessage());
                continue;
            }

            int updated = orderMapper.ship(o.getId(), carrierCode, carrierNameMap.get(carrierCode), waybillNo);
            if (updated == 0) {
                Order latest = orderMapper.selectById(o.getId());
                fails.add("第 " + lineNo + " 行：" + (overdueWithoutReason(latest, LocalDateTime.now())
                        ? delayReasonRequiredMessage(latest)
                        : "订单 " + orderNo + " 状态已变更"));
                continue;
            }
            o.setCarrier(carrierCode);
            o.setCarrierName(carrierNameMap.get(carrierCode));
            o.setWaybillNo(waybillNo);
            o.setStatus(Constants.ORDER_SHIPPED);
            o.setShipTime(LocalDateTime.now());
            addTrack(o, Constants.ORDER_SHIPPED, "商家已绑定「" + carrierNameMap.get(carrierCode)
                    + "」真实运单号 " + waybillNo + "，正在等待承运商揽收", "system");
            logisticsTrackingService.applyValidatedResult(o, verified);
            success++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("successCount", success);
        result.put("failCount", fails.size());
        result.put("failList", fails.size() > 200 ? fails.subList(0, 200) : fails);
        log.info("运单号导入完成 operator={} 成功={} 失败={}", operator, success, fails.size());
        return result;
    }

    /* ==================== 物流跟踪 ==================== */

    /**
     * 手动推进物流状态（真实项目由承运商回调或轨迹查询接口驱动）
     */
    @Transactional(rollbackFor = Exception.class)
    public void pushStatus(Long orderId, Integer status, String description, String operator) {
        if (orderId == null) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "缺少订单 ID");
        }
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (o.getStatus() == null || o.getStatus() < Constants.ORDER_SHIPPED) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ERROR, "订单尚未发货，无法推进物流状态");
        }
        o.setStatus(status);
        if (status == Constants.ORDER_SIGNED) {
            // 招标要求：核实是否真正入户送达
            o.setSignTime(LocalDateTime.now());
        }
        orderMapper.updateById(o);
        addTrack(o, status, description, "manual");
    }

    /**
     * 批量补录轨迹节点
     */
    @Transactional(rollbackFor = Exception.class)
    public void addTrackById(Long orderId, String description, LocalDateTime time) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        OrderTrack t = new OrderTrack();
        t.setOrderId(orderId);
        t.setOrderNo(o.getOrderNo());
        t.setStatus(o.getStatus());
        t.setDescription(description);
        t.setTrackTime(time == null ? LocalDateTime.now() : time);
        t.setSource("manual");
        trackMapper.insert(t);
    }

    public List<OrderTrack> tracks(Long orderId) {
        return trackMapper.selectList(new LambdaQueryWrapper<OrderTrack>()
                .eq(OrderTrack::getOrderId, orderId)
                .orderByDesc(OrderTrack::getTrackTime)
                .orderByDesc(OrderTrack::getId));
    }

    /**
     * 批量查询多个订单的轨迹。物流跟踪页需要展示「最新轨迹」，
     * 单条轮询会产生 N 次请求，这里一次取回后按订单分组。
     */
    public Map<String, List<OrderTrack>> tracksBatch(List<Long> orderIds) {
        Map<String, List<OrderTrack>> result = new LinkedHashMap<>();
        if (orderIds == null || orderIds.isEmpty()) {
            return result;
        }
        List<Long> ids = orderIds.stream().filter(Objects::nonNull).distinct()
                .limit(500).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return result;
        }
        List<OrderTrack> all = trackMapper.selectList(new LambdaQueryWrapper<OrderTrack>()
                .in(OrderTrack::getOrderId, ids)
                .orderByDesc(OrderTrack::getTrackTime)
                .orderByDesc(OrderTrack::getId));
        for (OrderTrack t : all) {
            result.computeIfAbsent(String.valueOf(t.getOrderId()), k -> new ArrayList<>()).add(t);
        }
        return result;
    }

    /**
     * 登记超时/延期原因。
     * 招标要求「收到配送信息后 10 日内发货」，超时必须说明原因并留存凭证，
     * 因此原因写入订单 exceptionReason，同时补一条轨迹便于对外解释。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markDelay(Long orderId, String type, String note, String eta, String operator) {
        if (orderId == null) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "缺少订单 ID");
        }
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (o.getStatus() == null || o.getStatus() != Constants.ORDER_PENDING) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ERROR, "仅待发货订单可登记超时原因");
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(type)) {
            sb.append(type.trim());
        }
        if (StringUtils.hasText(note)) {
            if (sb.length() > 0) {
                sb.append("｜");
            }
            sb.append(note.trim());
        }
        if (StringUtils.hasText(eta)) {
            if (sb.length() > 0) {
                sb.append("｜");
            }
            sb.append("预计发货：").append(eta.trim());
        }
        if (sb.length() == 0) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "请填写超时原因");
        }
        o.setExceptionReason(sb.toString());
        orderMapper.updateById(o);
        addTrack(o, Constants.ORDER_PENDING, "已登记超时说明：" + sb, "manual");
        log.info("登记超时原因 orderNo={} operator={} reason={}", o.getOrderNo(), operator, sb);
    }

    /**
     * 组装订单导出数据（不分页，上限 5 万行防止内存溢出）
     */
    public List<com.hz.delivery.excel.OrderExportRow> buildExportRows(Integer status, String org,
                                                                      String keyword, Integer slaLevel) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order> w =
                buildWrapper(status, org, keyword, slaLevel);
        w.orderByDesc(Order::getCreateTime).last("LIMIT 50000");
        List<Order> list = orderMapper.selectList(w);

        java.time.format.DateTimeFormatter dt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<com.hz.delivery.excel.OrderExportRow> rows = new ArrayList<>(list.size());
        for (Order o : list) {
            com.hz.delivery.excel.OrderExportRow r = new com.hz.delivery.excel.OrderExportRow();
            r.setOrderNo(o.getOrderNo());
            r.setEmpNo(o.getEmpNo());
            r.setGranteeName(o.getGranteeName());
            r.setOrg(o.getOrg());
            r.setDept(o.getDept());
            r.setPackageName(o.getPackageName());
            r.setQuantity(o.getQuantity());
            r.setReceiver(o.getReceiver());
            r.setPhone(o.getPhone());
            r.setAddress(GranteeService.fullAddress(o));
            r.setAllowStation(Boolean.TRUE.equals(o.getAllowStation()) ? "是" : "否");
            r.setStatusName(GranteeService.orderStatusName(o.getStatus()));
            r.setCarrierName(o.getCarrierName());
            r.setWaybillNo(o.getWaybillNo());
            r.setCreateTime(o.getCreateTime() == null ? null : o.getCreateTime().format(dt));
            r.setSlaDeadline(o.getSlaDeadline() == null ? null : o.getSlaDeadline().format(dt));
            r.setShipTime(o.getShipTime() == null ? null : o.getShipTime().format(dt));
            r.setSignTime(o.getSignTime() == null ? null : o.getSignTime().format(dt));
            r.setRemark(o.getRemark());
            // 实际发货时长：招标要求 10 日内发货，这里直接给出天数值便于核验
            if (o.getCreateTime() != null && o.getShipTime() != null) {
                long days = java.time.temporal.ChronoUnit.HOURS.between(o.getCreateTime(), o.getShipTime());
                r.setShipDays(String.format("%.1f", days / 24.0));
            } else {
                r.setShipDays("");
            }
            rows.add(r);
        }
        return rows;
    }

    /**
     * 拼装订单筛选条件（分页与导出共用，避免两处规则不一致）
     */
    private LambdaQueryWrapper<Order> buildWrapper(Integer status, String org, String keyword, Integer slaLevel) {
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(Order::getStatus, status);
        }
        if (StringUtils.hasText(org)) {
            w.eq(Order::getOrg, org);
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(x -> x.like(Order::getOrderNo, k)
                    .or().like(Order::getGranteeName, k)
                    .or().like(Order::getPhone, k)
                    .or().like(Order::getWaybillNo, k)
                    .or().like(Order::getReceiver, k));
        }
        if (slaLevel != null) {
            w.eq(Order::getStatus, Constants.ORDER_PENDING);
            switch (slaLevel) {
                case 3 -> w.apply("sla_deadline < NOW()");
                case 2 -> w.apply("sla_deadline >= NOW() AND sla_deadline < DATE_ADD(NOW(), INTERVAL 1 DAY)");
                case 1 -> w.apply("sla_deadline >= DATE_ADD(NOW(), INTERVAL 1 DAY) AND sla_deadline < DATE_ADD(NOW(), INTERVAL 3 DAY)");
                default -> { }
            }
        }
        return w;
    }

    /* ==================== 内部方法 ==================== */

    private boolean waybillExisted(String waybillNo) {
        return orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getWaybillNo, waybillNo)) > 0;
    }

    private boolean validWaybill(String waybillNo) {
        return StringUtils.hasText(waybillNo)
                && waybillNo.length() >= 6
                && waybillNo.length() <= 32;
    }

    /**
     * 超过承诺截止时间的订单必须先登记原因，才能进入发货流程。
     * Mapper 中还有同口径的原子条件，防止校验后、更新前恰好跨过截止时间。
     */
    private boolean overdueWithoutReason(Order o, LocalDateTime now) {
        return o != null
                && o.getSlaDeadline() != null
                && o.getSlaDeadline().isBefore(now)
                && !StringUtils.hasText(o.getExceptionReason());
    }

    private String delayReasonRequiredMessage(Order o) {
        return "订单 " + o.getOrderNo() + " 已超过承诺发货时限，请先登记超时原因后再发货";
    }

    private void addTrack(Order o, int status, String desc, String source) {
        OrderTrack t = new OrderTrack();
        t.setOrderId(o.getId());
        t.setOrderNo(o.getOrderNo());
        t.setStatus(status);
        t.setDescription(desc);
        t.setTrackTime(LocalDateTime.now());
        t.setSource(source);
        trackMapper.insert(t);
    }

    private void fillRemain(Order o) {
        if (o.getStatus() != null && o.getStatus() == Constants.ORDER_PENDING) {
            o.setRemainDays(SlaUtil.remainDays(LocalDateTime.now(), o.getSlaDeadline()));
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    public ImportBatch newBatch(String type, String fileName, int total, String operator) {
        ImportBatch b = new ImportBatch();
        b.setBatchNo("IB" + System.currentTimeMillis());
        b.setType(type);
        b.setFileName(fileName);
        b.setTotalCount(total);
        b.setSuccessCount(0);
        b.setFailCount(0);
        b.setOperator(operator);
        b.setCreateTime(LocalDateTime.now());
        return b;
    }
}
