package com.hz.delivery.service;

import com.hz.delivery.common.BizException;
import com.hz.delivery.common.Constants;
import com.hz.delivery.common.ErrorCode;
import com.hz.delivery.entity.Admin;
import com.hz.delivery.entity.Order;
import com.hz.delivery.entity.OrderTrack;
import com.hz.delivery.mapper.AdminMapper;
import com.hz.delivery.mapper.OrderMapper;
import com.hz.delivery.mapper.OrderTrackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

/** 用户确认与后台代确认共用一套签收规则，不调用物流平台。 */
@Service
@RequiredArgsConstructor
public class OrderReceiptService {
    private static final Set<Integer> CONFIRMABLE = Set.of(
            Constants.ORDER_SHIPPED, Constants.ORDER_TRANSIT, Constants.ORDER_DELIVERING);

    private final OrderMapper orderMapper;
    private final OrderTrackMapper trackMapper;
    private final AdminMapper adminMapper;

    public record Confirmation(Long orderId, String orderNo, int status,
                               LocalDateTime signTime, boolean changed) {}

    @Transactional(rollbackFor = Exception.class)
    public Confirmation confirmForClient(Long granteeId, Long orderId) {
        if (granteeId == null) throw BizException.of(ErrorCode.UNAUTHORIZED);
        return confirm(orderId, granteeId, "client", "收货人确认收货");
    }

    @Transactional(rollbackFor = Exception.class)
    public Confirmation confirmForAdmin(Long adminId, Long orderId, String remark) {
        Admin admin = adminId == null ? null : adminMapper.selectById(adminId);
        if (admin == null || !Integer.valueOf(1).equals(admin.getStatus())) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        if (!"admin".equalsIgnoreCase(admin.getRole()) && !"operator".equalsIgnoreCase(admin.getRole())) {
            throw BizException.of(ErrorCode.FORBIDDEN, "当前账号无代确认收货权限");
        }
        String note = remark == null ? "" : remark.trim();
        if (note.length() > 200) throw BizException.of(ErrorCode.PARAM_ERROR, "备注最多200字");
        // 操作人由服务端身份生成，和选填备注一起保存在轨迹中。
        String operator = StringUtils.hasText(admin.getRealName()) ? admin.getRealName() : admin.getUsername();
        String description = "后台代确认收货（" + operator + "）" + (note.isEmpty() ? "" : "：" + note);
        return confirm(orderId, null, "manual", description);
    }

    private Confirmation confirm(Long orderId, Long granteeId, String source, String description) {
        if (orderId == null) throw BizException.of(ErrorCode.PARAM_ERROR, "缺少订单ID");
        // 当前读锁住订单，两个确认请求只会有一个首次写入；签收更新与轨迹同事务提交。
        Order order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) throw BizException.of(ErrorCode.ORDER_NOT_FOUND);
        if (granteeId != null && !granteeId.equals(order.getGranteeId())) {
            throw BizException.of(ErrorCode.ORDER_NO_PERMISSION);
        }
        if (Integer.valueOf(Constants.ORDER_SIGNED).equals(order.getStatus())) {
            return result(order, false);
        }
        if (order.getStatus() == null || !CONFIRMABLE.contains(order.getStatus())) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ERROR, "仅已发货、运输中、派送中的订单可确认收货");
        }
        if (!StringUtils.hasText(order.getWaybillNo()) || order.getShipTime() == null) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ERROR, "订单缺少有效发货信息，无法确认收货");
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime signedAt = order.getSignTime() == null ? now : order.getSignTime();
        if (orderMapper.confirmReceipt(orderId, granteeId, signedAt) != 1) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ERROR, "订单状态已变更，请刷新后重试");
        }
        OrderTrack track = new OrderTrack();
        track.setOrderId(orderId);
        track.setOrderNo(order.getOrderNo());
        track.setStatus(Constants.ORDER_SIGNED);
        track.setDescription(description);
        track.setTrackTime(now);
        track.setSource(source);
        trackMapper.insert(track);
        order.setStatus(Constants.ORDER_SIGNED);
        order.setSignTime(signedAt);
        return result(order, true);
    }

    private Confirmation result(Order order, boolean changed) {
        return new Confirmation(order.getId(), order.getOrderNo(), order.getStatus(), order.getSignTime(), changed);
    }
}
