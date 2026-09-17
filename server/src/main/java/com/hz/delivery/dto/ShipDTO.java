package com.hz.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 发货录入
 */
@Data
public class ShipDTO {

    @NotEmpty(message = "请选择要发货的订单")
    private List<Long> orderIds;

    @NotBlank(message = "请选择承运商")
    private String carrier;

    /** 单条录入时的运单号；批量时可为空，系统按规则生成 */
    private String waybillNo;
}
