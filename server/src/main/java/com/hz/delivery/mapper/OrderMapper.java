package com.hz.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.delivery.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM t_order WHERE order_no = #{orderNo} AND deleted = 0 LIMIT 1")
    Order selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 生成订单号时的当日序号（保证同日订单号连续可读）
     */
    @Select("SELECT COUNT(*) FROM t_order WHERE order_no LIKE CONCAT(#{prefix}, '%')")
    int countByOrderNoPrefix(@Param("prefix") String prefix);

    /**
     * 发货：条件带 status = 0，避免重复发货
     */
    @Update("UPDATE t_order SET status = 10, carrier = #{carrier}, carrier_name = #{carrierName}, " +
            "waybill_no = #{waybillNo}, ship_time = NOW(), update_time = NOW() " +
            "WHERE id = #{id} AND status = 0 AND deleted = 0")
    int ship(@Param("id") Long id,
             @Param("carrier") String carrier,
             @Param("carrierName") String carrierName,
             @Param("waybillNo") String waybillNo);

    /**
     * 看板：按状态分组统计
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM t_order WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countGroupByStatus();

    /**
     * 看板 / 报表：按单位分组统计
     */
    @Select("SELECT org, COUNT(*) AS total, " +
            "SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS pending, " +
            "SUM(CASE WHEN status = 10 THEN 1 ELSE 0 END) AS shipped, " +
            "SUM(CASE WHEN status IN (20,30) THEN 1 ELSE 0 END) AS transit, " +
            "SUM(CASE WHEN status = 40 THEN 1 ELSE 0 END) AS signed " +
            "FROM t_order WHERE deleted = 0 GROUP BY org ORDER BY total DESC")
    List<Map<String, Object>> countGroupByOrg();

    /**
     * 看板：按套餐分组统计
     */
    @Select("SELECT package_name AS name, COUNT(*) AS cnt, IFNULL(SUM(quantity),0) AS qty " +
            "FROM t_order WHERE deleted = 0 GROUP BY package_name ORDER BY cnt DESC")
    List<Map<String, Object>> countGroupByPackage();

    /**
     * SLA 统计：待发货订单中已超时 / 临近超时
     */
    @Select("SELECT " +
            "SUM(CASE WHEN sla_deadline < NOW() THEN 1 ELSE 0 END) AS overdue, " +
            "SUM(CASE WHEN sla_deadline >= NOW() AND sla_deadline < DATE_ADD(NOW(), INTERVAL 1 DAY) THEN 1 ELSE 0 END) AS within1d, " +
            "SUM(CASE WHEN sla_deadline >= DATE_ADD(NOW(), INTERVAL 1 DAY) AND sla_deadline < DATE_ADD(NOW(), INTERVAL 3 DAY) THEN 1 ELSE 0 END) AS within3d, " +
            "COUNT(*) AS total " +
            "FROM t_order WHERE status = 0 AND deleted = 0")
    Map<String, Object> slaSummary();

    /**
     * 近 N 天的下单趋势
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day, COUNT(*) AS cnt " +
            "FROM t_order WHERE deleted = 0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY day ORDER BY day")
    List<Map<String, Object>> countTrend(@Param("days") int days);
}
