package com.hz.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hz.delivery.entity.GoodsPackage;
import com.hz.delivery.entity.Grantee;
import com.hz.delivery.mapper.GoodsPackageMapper;
import com.hz.delivery.mapper.GranteeMapper;
import com.hz.delivery.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 数据看板与报表
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final OrderMapper orderMapper;
    private final GranteeMapper granteeMapper;
    private final GoodsPackageMapper packageMapper;

    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();

        // ① 订单状态分布
        List<Map<String, Object>> statusRows = orderMapper.countGroupByStatus();
        Map<Integer, Long> statusMap = new LinkedHashMap<>();
        for (Map<String, Object> row : statusRows) {
            Integer st = ((Number) row.get("status")).intValue();
            Long cnt = ((Number) row.get("cnt")).longValue();
            statusMap.put(st, cnt);
        }
        long total = statusMap.values().stream().mapToLong(Long::longValue).sum();
        long pending = statusMap.getOrDefault(0, 0L);
        long shipped = statusMap.getOrDefault(10, 0L);
        long transit = statusMap.getOrDefault(20, 0L) + statusMap.getOrDefault(30, 0L);
        long signed = statusMap.getOrDefault(40, 0L);
        long abnormal = statusMap.getOrDefault(60, 0L) + statusMap.getOrDefault(70, 0L);
        long canceled = statusMap.getOrDefault(50, 0L);

        Map<String, Object> orderStat = new LinkedHashMap<>();
        orderStat.put("total", total);
        orderStat.put("pending", pending);
        orderStat.put("shipped", shipped);
        orderStat.put("transit", transit);
        orderStat.put("signed", signed);
        orderStat.put("abnormal", abnormal);
        orderStat.put("canceled", canceled);
        orderStat.put("signRate", total == 0 ? 0 : Math.round(signed * 1000.0 / total) / 10.0);
        result.put("orderStat", orderStat);

        // ② 名单领取情况
        long granteeTotal = granteeMapper.selectCount(new LambdaQueryWrapper<>());
        long claimed = granteeMapper.selectCount(new LambdaQueryWrapper<Grantee>().apply("used > 0"));
        Map<String, Object> granteeStat = new LinkedHashMap<>();
        granteeStat.put("total", granteeTotal);
        granteeStat.put("claimed", claimed);
        granteeStat.put("unclaimed", granteeTotal - claimed);
        granteeStat.put("claimRate", granteeTotal == 0 ? 0 : Math.round(claimed * 1000.0 / granteeTotal) / 10.0);
        result.put("granteeStat", granteeStat);

        // ③ SLA 履约：距承诺发货截止时间的分布
        Map<String, Object> sla = orderMapper.slaSummary();
        Map<String, Object> slaStat = new LinkedHashMap<>();
        slaStat.put("pendingTotal", num(sla, "total"));
        slaStat.put("overdue", num(sla, "overdue"));
        slaStat.put("within1d", num(sla, "within1d"));
        slaStat.put("within3d", num(sla, "within3d"));
        result.put("slaStat", slaStat);

        // ④ 单位维度
        result.put("orgStat", orderMapper.countGroupByOrg());

        // ⑤ 套餐维度
        result.put("packageStat", orderMapper.countGroupByPackage());

        // ⑥ 近 7 天趋势
        result.put("trend", fillTrend(orderMapper.countTrend(7)));

        // ⑦ 库存预警
        List<GoodsPackage> lowStock = packageMapper.selectList(new LambdaQueryWrapper<GoodsPackage>()
                .eq(GoodsPackage::getStatus, 1)
                .apply("stock <= warn")
                .orderByAsc(GoodsPackage::getStock));
        List<Map<String, Object>> lowStockList = new ArrayList<>();
        for (GoodsPackage p : lowStock) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("no", p.getNo());
            m.put("stock", p.getStock());
            m.put("warn", p.getWarn());
            lowStockList.add(m);
        }
        result.put("lowStock", lowStockList);

        return result;
    }

    /**
     * 报表：按单位 / 按套餐的领取明细汇总
     */
    public Map<String, Object> report() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orgStat", orderMapper.countGroupByOrg());
        result.put("packageStat", orderMapper.countGroupByPackage());
        result.put("trend", fillTrend(orderMapper.countTrend(30)));
        return result;
    }

    /** 补齐缺失日期，避免前端折线图断点 */
    private List<Map<String, Object>> fillTrend(List<Map<String, Object>> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            map.put(String.valueOf(r.get("day")), ((Number) r.get("cnt")).longValue());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 13; i >= 0; i--) {
            String day = today.minusDays(i).toString();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", day);
            m.put("cnt", map.getOrDefault(day, 0L));
            out.add(m);
        }
        return out;
    }

    private long num(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }
}
