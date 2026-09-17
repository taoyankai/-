package com.hz.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 套餐商品明细
 */
@Data
@TableName("t_package_item")
public class PackageItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long packageId;

    /** 商品名称 */
    private String name;

    /** 规格，如 5kg/袋 */
    private String spec;

    /** 数量 */
    private Integer qty;

    /** 单位 */
    private String unit;

    private Integer sort;
}
