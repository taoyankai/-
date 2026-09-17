package com.hz.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hz.delivery.common.Constants;
import com.hz.delivery.config.BizProperties;
import com.hz.delivery.entity.Order;
import com.hz.delivery.entity.SlaAlert;
import com.hz.delivery.mapper.OrderMapper;
import com.hz.delivery.mapper.SlaAlertMapper;
import com.hz.delivery.service.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SLA 发货预警。
 *
 * <p>「收到配送信息后 10 日内发货」是招标文件的硬性履约指标。仅有后台筛选是不够的
 * —— 没人点开筛选，临近超期的订单就永远不会被看见。因此这里改成每日主动扫描：
 * 按「已超期 / 24 小时内到期 / 72 小时内到期」三档落库供看板标红，
 * 并在出现超期或次日到期订单时短信通知库房管理员。
 *
 * <p>分档口径与后台列表筛选（{@code slaLevel}）完全一致，避免「看板说没事、列表说超期」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaAlertService {

    /** 每档最多登记多少个订单号，避免超出字段长度 */
    private static final int MAX_NOS = 30;

    private final OrderMapper orderMapper;
    private final SlaAlertMapper alertMapper;
    private final SmsSender smsSender;
    private final BizProperties props;

    /**
     * 扫描待发货订单并生成预警。
     *
     * @param notify 是否发送短信通知（手动触发时可按需关闭，避免重复打扰库管）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scan(boolean notify) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<Order> pending = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, Constants.ORDER_PENDING)
                .isNotNull(Order::getSlaDeadline)
                .orderByAsc(Order::getSlaDeadline));

        Map<Integer, List<Order>> buckets = new LinkedHashMap<>();
        buckets.put(3, new ArrayList<>());
        buckets.put(2, new ArrayList<>());
        buckets.put(1, new ArrayList<>());
        for (Order o : pending) {
            int lv = levelOf(now, o.getSlaDeadline());
            if (lv > 0) {
                buckets.get(lv).add(o);
            }
        }

        for (Map.Entry<Integer, List<Order>> e : buckets.entrySet()) {
            upsert(today, e.getKey(), e.getValue());
        }

        int overdue = buckets.get(3).size();
        int dueIn24h = buckets.get(2).size();
        int dueIn72h = buckets.get(1).size();

        String phone = props.getSlaAlertPhone();
        String smsResp = null;
        if (notify && (overdue > 0 || dueIn24h > 0)) {
            if (StringUtils.hasText(phone)) {
                String text = String.format("【华中商贸】发货预警：已超期 %d 笔，24 小时内到期 %d 笔，请尽快发货。",
                        overdue, dueIn24h);
                smsResp = sendAll(phone, text);
                markNotified(today, smsResp);
            } else {
                log.warn("存在发货超时预警（已超期 {} 笔 / 24 小时内到期 {} 笔），"
                        + "但未配置 SLA_ALERT_PHONE，仅落库未发送短信", overdue, dueIn24h);
            }
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("alertDate", today.toString());
        r.put("overdue", overdue);
        r.put("dueIn24h", dueIn24h);
        r.put("dueIn72h", dueIn72h);
        r.put("pendingTotal", pending.size());
        r.put("notified", smsResp != null);
        r.put("notifyResp", smsResp);
        log.info("SLA 预警扫描完成 date={} 已超期={} 24h内={} 72h内={} 待发货合计={}",
                today, overdue, dueIn24h, dueIn72h, pending.size());
        return r;
    }

    /**
     * 最近预警记录（后台看板）
     */
    public List<SlaAlert> recent(Integer days) {
        int d = (days == null || days < 1 || days > 90) ? 14 : days;
        return alertMapper.selectList(new LambdaQueryWrapper<SlaAlert>()
                .ge(SlaAlert::getAlertDate, LocalDate.now().minusDays(d))
                .orderByDesc(SlaAlert::getAlertDate)
                .orderByDesc(SlaAlert::getLevel));
    }

    /* ==================== 内部方法 ==================== */

    /**
     * 预警分档：与后台列表 slaLevel 筛选口径一致
     */
    private int levelOf(LocalDateTime now, LocalDateTime deadline) {
        if (deadline.isBefore(now)) {
            return 3;
        }
        if (deadline.isBefore(now.plusDays(1))) {
            return 2;
        }
        if (deadline.isBefore(now.plusDays(3))) {
            return 1;
        }
        return 0;
    }

    /** 当天该档已有记录则更新，否则新增（重复扫描不会产生多条） */
    private void upsert(LocalDate date, int level, List<Order> orders) {
        SlaAlert exist = alertMapper.selectOne(new LambdaQueryWrapper<SlaAlert>()
                .eq(SlaAlert::getAlertDate, date)
                .eq(SlaAlert::getLevel, level)
                .last("LIMIT 1"));
        String nos = orders.stream().limit(MAX_NOS).map(Order::getOrderNo).collect(Collectors.joining(","));
        if (exist == null) {
            SlaAlert a = new SlaAlert();
            a.setAlertDate(date);
            a.setLevel(level);
            a.setOrderCount(orders.size());
            a.setOrderNos(nos);
            a.setNotified(0);
            a.setCreateTime(LocalDateTime.now());
            alertMapper.insert(a);
        } else {
            exist.setOrderCount(orders.size());
            exist.setOrderNos(nos);
            alertMapper.updateById(exist);
        }
    }

    private void markNotified(LocalDate date, String resp) {
        List<SlaAlert> list = alertMapper.selectList(new LambdaQueryWrapper<SlaAlert>()
                .eq(SlaAlert::getAlertDate, date)
                .ge(SlaAlert::getLevel, 2)
                .gt(SlaAlert::getOrderCount, 0));
        for (SlaAlert a : list) {
            a.setNotified(1);
            a.setNotifyResp(resp == null || resp.length() <= 200 ? resp : resp.substring(0, 200));
            alertMapper.updateById(a);
        }
    }

    /** 支持配置多个接收号码（英文逗号分隔）；单个失败不影响其他号码 */
    private String sendAll(String phones, String text) {
        StringBuilder sb = new StringBuilder();
        for (String p : phones.split(",")) {
            String phone = p.trim();
            if (!StringUtils.hasText(phone)) {
                continue;
            }
            try {
                String resp = smsSender.sendText(phone, text);
                sb.append(phone).append(':').append(resp).append(';');
            } catch (Exception e) {
                sb.append(phone).append(":失败(").append(e.getMessage()).append(");");
                log.warn("SLA 预警短信发送失败 phone={} err={}", phone, e.getMessage());
            }
        }
        return sb.toString();
    }
}
