package com.hz.delivery.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 兼容旧人工推进入口；当前仅允许代确认收货。 */
@Data
public class OrderStatusPushDTO {
    @NotNull(message = "请选择目标状态")
    private Integer status;

    @Size(max = 200, message = "备注最多200字")
    private String description;
}
