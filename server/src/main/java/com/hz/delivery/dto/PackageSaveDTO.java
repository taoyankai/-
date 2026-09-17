package com.hz.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 套餐新增 / 修改
 * 招标要求：不允许私自更改方案内容，因此每次改动必须填写变更原因并留痕。
 */
@Data
public class PackageSaveDTO {

    private Long id;

    private String no;

    @NotBlank(message = "请填写套餐名称")
    private String name;

    private String sub;

    private BigDecimal price;

    private Integer stock;

    private Integer warn;

    private Integer sort;

    private Integer status;

    /** 变更原因（必填，用于留痕） */
    private String changeReason;

    private List<Item> goods;

    @Data
    public static class Item {
        private String name;
        private String spec;
        private Integer qty;
        private String unit;
    }
}
