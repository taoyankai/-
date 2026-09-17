package com.hz.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流轨迹节点
 */
@Data
@TableName("t_order_track")
public class OrderTrack {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    /** 该节点对应的订单状态 */
    private Integer status;

    /** 轨迹文案 */
    private String description;

    /** 轨迹发生时间 */
    private LocalDateTime trackTime;

    /** 数据来源：system 系统 / manual 人工录入 / carrier 承运商回传 */
    private String source;
}
