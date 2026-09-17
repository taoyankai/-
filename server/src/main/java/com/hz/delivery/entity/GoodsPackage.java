package com.hz.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 慰问品套餐
 */
@Data
@TableName("t_package")
public class GoodsPackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 套餐编号，如 PKA2026 */
    private String no;

    /** 套餐名称 */
    private String name;

    /** 副标题 / 卖点 */
    private String sub;

    /** 封面图 */
    private String cover;

    /** 市场参考价（仅供展示，本项目不涉及用户支付） */
    private BigDecimal price;

    /** 库存 */
    private Integer stock;

    /** 库存预警阈值 */
    private Integer warn;

    /** 排序 */
    private Integer sort;

    /** 状态：1 上架，0 下架 */
    private Integer status;

    /** 变更原因（招标要求：不允许私自更改方案内容，每次改动留痕） */
    private String changeReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 商品明细，非数据库字段 */
    @TableField(exist = false)
    private List<PackageItem> goods;
}
