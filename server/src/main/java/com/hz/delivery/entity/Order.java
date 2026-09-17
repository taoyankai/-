package com.hz.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 配送订单
 */
@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号，如 HZ202609150001 */
    private String orderNo;

    /* ---------- 领取人信息（冗余，便于后台检索与导出） ---------- */
    private Long granteeId;
    private String empNo;
    private String granteeName;
    private String org;
    private String dept;

    /* ---------- 套餐信息（快照，套餐变更不影响历史订单） ---------- */
    private Long packageId;
    private String packageName;
    private Integer quantity;
    /** 套餐内容快照，JSON 字符串 */
    private String goodsSnapshot;

    /* ---------- 配送信息 ---------- */
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    /** 是否允许放代收点。招标要求：默认不允许，必须送货上门 */
    private Boolean allowStation;
    private String remark;

    /**
     * 收货地址自助修改次数。
     * 未发货前允许用户自行改 1 次（写轨迹留痕），发货后锁定，
     * 避免半路改派导致丢件；超出后需联系客服人工处理。
     */
    private Integer addrChangeCount;

    /** 用户下单时在合作物流中选择的期望承运商编码（包邮自选；实际发货承运商以 carrier 为准） */
    private String expectCarrier;
    /** 期望承运商名称 */
    private String expectCarrierName;

    /* ---------- 履约信息 ---------- */
    /** 参考 Constants.ORDER_* */
    private Integer status;
    private String carrier;
    private String carrierName;
    private String waybillNo;
    private String exceptionReason;

    private LocalDateTime createTime;
    /** 承诺发货截止时间 = createTime + slaDays */
    private LocalDateTime slaDeadline;
    private LocalDateTime shipTime;
    private LocalDateTime signTime;
    private LocalDateTime cancelTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /* ---------- 非数据库字段 ---------- */

    /** 配送内容快照，解析自 goodsSnapshot，供前端直接渲染 */
    @TableField(exist = false)
    private List<GoodsSnapshotItem> goods;

    /** 物流轨迹，仅在详情接口填充 */
    @TableField(exist = false)
    private List<OrderTrack> traces;

    /** 距离承诺发货截止的剩余天数：正数表示还有几天，负数表示已超期 */
    @TableField(exist = false)
    private Long remainDays;

    @Data
    public static class GoodsSnapshotItem {
        private String name;
        private String spec;
        private Integer qty;
        private String unit;
    }
}
