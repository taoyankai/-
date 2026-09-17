package com.hz.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hz.delivery.common.BizException;
import com.hz.delivery.common.Constants;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.common.SlaUtil;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.dto.OrderAddressDTO;
import com.hz.delivery.dto.OrderCreateDTO;
import com.hz.delivery.entity.*;
import com.hz.delivery.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单服务：提交配送信息 → 待发货池 → 查询 → 取消
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ORDER_NO_SEQ_KEY = "hz:orderno:";

    /** 未发货前允许用户自助修改收货地址的次数（发货后彻底锁定） */
    private static final int MAX_ADDR_CHANGE = 1;

    private final OrderMapper orderMapper;
    private final OrderTrackMapper trackMapper;
    private final GranteeMapper granteeMapper;
    private final GoodsPackageMapper packageMapper;
    private final PackageItemMapper itemMapper;
    private final AddressMapper addressMapper;
    private final CarrierMapper carrierMapper;
    private final StringRedisTemplate redis;
    private final BizProperties props;

    private final ObjectMapper json = new ObjectMapper();

    /**
     * 提交配送信息（下单）
     */
    @Transactional(rollbackFor = Exception.class)
    public Order create(Long granteeId, OrderCreateDTO dto) {
        Grantee g = granteeMapper.selectById(granteeId);
        if (g == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        if (g.getStatus() == null || g.getStatus() != 1) {
            throw BizException.of(ErrorCode.GRANTEE_DISABLED);
        }

        // ① 地址完整度校验：服务端是最终防线，前端拦截只是体验优化
        String addrErr = AddressValidator.validate(dto.getReceiver(), dto.getPhone(),
                dto.getProvince(), dto.getCity(), dto.getDistrict(), dto.getDetail());
        if (addrErr != null) {
            throw BizException.of(ErrorCode.ADDRESS_INCOMPLETE, addrErr);
        }

        // ② 幂等：同一套餐已有待发货订单，拒绝重复提交
        //    必须排在额度校验之前 —— 否则用户重复点击提交时收到的是「每人限领 1 份」，
        //    与该提示对应的真实原因（重复提交）不符
        Long dup = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getGranteeId, granteeId)
                .eq(Order::getPackageId, dto.getPackageId())
                .eq(Order::getStatus, Constants.ORDER_PENDING));
        if (dup != null && dup > 0) {
            throw BizException.of(ErrorCode.ORDER_DUPLICATE);
        }

        // ③ 领取额度校验：每人限领 1 份（换套餐再次领取时命中此项）
        int quota = g.getQuota() == null ? props.getDefaultQuota() : g.getQuota();
        int used = g.getUsed() == null ? 0 : g.getUsed();
        if (used >= quota) {
            throw BizException.of(ErrorCode.QUOTA_EXHAUSTED);
        }

        // ④ 套餐有效性
        GoodsPackage pkg = packageMapper.selectById(dto.getPackageId());
        if (pkg == null || pkg.getStatus() == null || pkg.getStatus() != 1) {
            throw BizException.of(ErrorCode.PACKAGE_OFFLINE);
        }
        int qty = (dto.getQuantity() == null || dto.getQuantity() < 1) ? 1 : dto.getQuantity();

        // ⑤ 原子扣减库存（条件 stock >= qty，防超卖）
        if (packageMapper.deductStock(pkg.getId(), qty) == 0) {
            throw BizException.of(ErrorCode.PACKAGE_NO_STOCK);
        }

        // ⑥ 原子扣减额度（条件 used < quota，防并发超额）
        //    失败时直接抛异常，由事务统一回滚库存
        if (granteeMapper.consumeQuota(granteeId) == 0) {
            throw BizException.of(ErrorCode.QUOTA_EXHAUSTED);
        }

        // ⑦ 套餐内容快照：套餐后续被修改不影响历史订单
        List<PackageItem> items = itemMapper.selectList(new LambdaQueryWrapper<PackageItem>()
                .eq(PackageItem::getPackageId, pkg.getId())
                .orderByAsc(PackageItem::getSort));
        List<Order.GoodsSnapshotItem> snapshot = items.stream().map(i -> {
            Order.GoodsSnapshotItem si = new Order.GoodsSnapshotItem();
            si.setName(i.getName());
            si.setSpec(i.getSpec());
            si.setQty(i.getQty());
            si.setUnit(i.getUnit());
            return si;
        }).collect(Collectors.toList());

        // ⑧ 期望承运商：包邮配送，用户在合作物流中自选；缺省取第一家启用的承运商
        Carrier expectCarrier;
        if (StringUtils.hasText(dto.getCarrierCode())) {
            expectCarrier = carrierMapper.selectOne(new LambdaQueryWrapper<Carrier>()
                    .eq(Carrier::getCode, dto.getCarrierCode().trim())
                    .eq(Carrier::getStatus, 1)
                    .last("LIMIT 1"));
            if (expectCarrier == null) {
                throw BizException.of(ErrorCode.PARAM_ERROR, "所选承运商不可用，请重新选择");
            }
        } else {
            expectCarrier = carrierMapper.selectOne(new LambdaQueryWrapper<Carrier>()
                    .eq(Carrier::getStatus, 1)
                    .orderByAsc(Carrier::getSort)
                    .last("LIMIT 1"));
            if (expectCarrier == null) {
                throw BizException.of(ErrorCode.CARRIER_REQUIRED, "暂无可用的承运商，请联系运营人员");
            }
        }

        // ⑨ 落库
        LocalDateTime now = LocalDateTime.now();
        Order o = new Order();
        o.setOrderNo(nextOrderNo());
        o.setGranteeId(granteeId);
        o.setEmpNo(g.getEmpNo());
        o.setGranteeName(g.getName());
        o.setOrg(g.getOrg());
        o.setDept(g.getDept());
        o.setPackageId(pkg.getId());
        o.setPackageName(pkg.getName());
        o.setQuantity(qty);
        o.setGoodsSnapshot(writeJson(snapshot));
        o.setReceiver(dto.getReceiver().trim());
        o.setPhone(dto.getPhone().trim());
        o.setProvince(dto.getProvince());
        o.setCity(dto.getCity());
        o.setDistrict(dto.getDistrict());
        o.setDetail(dto.getDetail().trim());
        o.setAllowStation(Boolean.TRUE.equals(dto.getAllowStation()));
        o.setRemark(dto.getRemark());
        o.setExpectCarrier(expectCarrier.getCode());
        o.setExpectCarrierName(expectCarrier.getName());
        o.setStatus(Constants.ORDER_PENDING);
        o.setCreateTime(now);
        // 承诺发货截止时间：收到配送信息后 N 日内发货
        o.setSlaDeadline(now.plusDays(props.getSlaDays()));
        orderMapper.insert(o);

        // ⑨ 首条轨迹
        addTrack(o.getId(), o.getOrderNo(), Constants.ORDER_PENDING,
                "您的配送信息已提交成功，我们将于 " + props.getSlaDays() + " 日内安排发货", "system");

        // ⑩ 可选：存入地址簿，下次免填
        if (Boolean.TRUE.equals(dto.getSaveAddress())) {
            saveToAddressBook(granteeId, dto);
        }

        log.info("下单成功 orderNo={} granteeId={} package={}", o.getOrderNo(), granteeId, pkg.getName());
        return detail(granteeId, o.getId());
    }

    /**
     * 我的订单列表
     */
    public List<Order> listByGrantee(Long granteeId, Integer status) {
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<Order>()
                .eq(Order::getGranteeId, granteeId)
                .orderByDesc(Order::getCreateTime);
        if (status != null) {
            w.eq(Order::getStatus, status);
        }
        List<Order> list = orderMapper.selectList(w);
        list.forEach(this::fillDerived);
        return list;
    }

    /**
     * 订单详情（含物流轨迹）
     */
    public Order detail(Long granteeId, Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (granteeId != null && !granteeId.equals(o.getGranteeId())) {
            throw BizException.of(ErrorCode.ORDER_NO_PERMISSION);
        }
        List<OrderTrack> traces = trackMapper.selectList(new LambdaQueryWrapper<OrderTrack>()
                .eq(OrderTrack::getOrderId, orderId)
                .orderByDesc(OrderTrack::getTrackTime)
                .orderByDesc(OrderTrack::getId));
        o.setTraces(traces);
        fillDerived(o);
        return o;
    }

    /**
     * 按运单号查询物流轨迹（仅限本人订单）。
     * 即使用户没记住订单号，也能凭快递单上的运单号查询配送进度。
     */
    public Order trackByWaybill(Long granteeId, String waybillNo) {
        if (!StringUtils.hasText(waybillNo)) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "请填写运单号");
        }
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getWaybillNo, waybillNo.trim())
                .last("LIMIT 1"));
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND, "未查询到该运单号对应的订单，请核对后重试");
        }
        if (granteeId != null && !granteeId.equals(o.getGranteeId())) {
            throw BizException.of(ErrorCode.ORDER_NO_PERMISSION, "该运单号不属于您的订单");
        }
        return detail(granteeId, o.getId());
    }

    /**
     * 用户取消订单：仅待发货可取消，取消后回补库存与领取额度
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long granteeId, Long orderId, String reason) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (granteeId != null && !granteeId.equals(o.getGranteeId())) {
            throw BizException.of(ErrorCode.ORDER_NO_PERMISSION);
        }
        if (o.getStatus() == null || o.getStatus() != Constants.ORDER_PENDING) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ERROR, "订单已发货，无法取消，请联系客服");
        }

        int updated = orderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, Constants.ORDER_PENDING)
                .set(Order::getStatus, Constants.ORDER_CANCELED)
                .set(Order::getCancelTime, LocalDateTime.now()));
        if (updated == 0) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ERROR, "订单状态已变更，请刷新后重试");
        }

        packageMapper.restoreStock(o.getPackageId(), o.getQuantity());
        granteeMapper.releaseQuota(o.getGranteeId());
        addTrack(orderId, o.getOrderNo(), Constants.ORDER_CANCELED,
                StringUtils.hasText(reason) ? "订单已取消：" + reason : "订单已取消", "system");
    }

    /**
     * 用户自助修改收货地址（未发货前）。
     *
     * 规则与招标要求配套：
     * ① 仅「待发货」可改 —— 发货后地址已随运单下发承运商，自行修改会造成丢件；
     * ② 只能自助改 1 次 —— 防止临近发货反复变更导致错发，超出需联系客服；
     * ③ 修改必须写入轨迹 —— 何时改了地址、改成什么，发货前可核对、发货后可追溯。
     */
    @Transactional(rollbackFor = Exception.class)
    public Order updateAddress(Long granteeId, Long orderId, OrderAddressDTO dto) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        if (granteeId != null && !granteeId.equals(o.getGranteeId())) {
            throw BizException.of(ErrorCode.ORDER_NO_PERMISSION);
        }
        if (o.getStatus() == null || o.getStatus() != Constants.ORDER_PENDING) {
            throw BizException.of(ErrorCode.ORDER_ADDR_LOCKED);
        }
        int changed = o.getAddrChangeCount() == null ? 0 : o.getAddrChangeCount();
        if (changed >= MAX_ADDR_CHANGE) {
            throw BizException.of(ErrorCode.ORDER_ADDR_LIMIT);
        }

        // 与下单共用同一套地址完整度规则，服务端仍是最终防线
        String addrErr = AddressValidator.validate(dto.getReceiver(), dto.getPhone(),
                dto.getProvince(), dto.getCity(), dto.getDistrict(), dto.getDetail());
        if (addrErr != null) {
            throw BizException.of(ErrorCode.ADDRESS_INCOMPLETE, addrErr);
        }

        String before = o.getProvince() + o.getCity() + o.getDistrict() + o.getDetail();
        String after = dto.getProvince() + dto.getCity() + dto.getDistrict() + dto.getDetail();

        // 条件更新带 status 与次数双重条件：并发重复提交时只有一次能生效
        int updated = orderMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .eq(Order::getStatus, Constants.ORDER_PENDING)
                        .eq(Order::getAddrChangeCount, changed)
                        .set(Order::getReceiver, dto.getReceiver().trim())
                        .set(Order::getPhone, dto.getPhone().trim())
                        .set(Order::getProvince, dto.getProvince())
                        .set(Order::getCity, dto.getCity())
                        .set(Order::getDistrict, dto.getDistrict())
                        .set(Order::getDetail, dto.getDetail().trim())
                        .set(Order::getAllowStation, Boolean.TRUE.equals(dto.getAllowStation()))
                        .set(Order::getRemark, dto.getRemark())
                        .set(Order::getAddrChangeCount, changed + 1));
        if (updated == 0) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ERROR, "订单状态已变更，请刷新后重试");
        }

        // 轨迹会下发给用户端，因此只登记「改成了什么」，不回显旧地址
        int left = MAX_ADDR_CHANGE - changed - 1;
        String trackDesc = before.equals(after)
                ? "您已更新配送信息备注；剩余自助修改次数 " + left + " 次"
                : "您已修改收货地址为：" + after + "；剩余自助修改次数 " + left + " 次";
        addTrack(orderId, o.getOrderNo(), Constants.ORDER_PENDING, trackDesc, "system");

        log.info("用户修改收货地址 orderNo={} granteeId={} 第 {} 次", o.getOrderNo(), granteeId, changed + 1);
        return detail(granteeId, orderId);
    }

    /* ==================== 内部方法 ==================== */

    /**
     * 订单号：HZ + yyyyMMdd + 4 位当日序号，用 Redis 自增保证并发下不重复
     */
    private String nextOrderNo() {
        String day = LocalDateTime.now().format(DAY_FMT);
        String key = ORDER_NO_SEQ_KEY + day;
        Long seq = redis.opsForValue().increment(key);
        redis.expire(key, 2, TimeUnit.DAYS);
        return "HZ" + day + String.format("%04d", seq == null ? 1 : seq);
    }

    private void addTrack(Long orderId, String orderNo, int status, String desc, String source) {
        OrderTrack t = new OrderTrack();
        t.setOrderId(orderId);
        t.setOrderNo(orderNo);
        t.setStatus(status);
        t.setDescription(desc);
        t.setTrackTime(LocalDateTime.now());
        t.setSource(source);
        trackMapper.insert(t);
    }

    /** 填充非数据库字段：剩余天数、商品明细 */
    private void fillDerived(Order o) {
        o.setRemainDays(SlaUtil.remainDays(LocalDateTime.now(), o.getSlaDeadline()));
        if (StringUtils.hasText(o.getGoodsSnapshot())) {
            try {
                o.setGoods(json.readValue(o.getGoodsSnapshot(),
                        new TypeReference<List<Order.GoodsSnapshotItem>>() {}));
            } catch (Exception e) {
                o.setGoods(new ArrayList<>());
            }
        }
    }

    private String writeJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void saveToAddressBook(Long granteeId, OrderCreateDTO dto) {
        Long exists = addressMapper.selectCount(new LambdaQueryWrapper<Address>()
                .eq(Address::getGranteeId, granteeId)
                .eq(Address::getDetail, dto.getDetail().trim())
                .eq(Address::getPhone, dto.getPhone().trim()));
        if (exists != null && exists > 0) {
            return;
        }
        Long total = addressMapper.selectCount(new LambdaQueryWrapper<Address>()
                .eq(Address::getGranteeId, granteeId));
        Address a = new Address();
        a.setGranteeId(granteeId);
        a.setName(dto.getReceiver().trim());
        a.setPhone(dto.getPhone().trim());
        a.setProvince(dto.getProvince());
        a.setCity(dto.getCity());
        a.setDistrict(dto.getDistrict());
        a.setDetail(dto.getDetail().trim());
        a.setAllowStation(Boolean.TRUE.equals(dto.getAllowStation()));
        a.setIsDefault(total == null || total == 0);
        addressMapper.insert(a);
    }
}
