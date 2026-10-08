package com.hz.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 批量发货中的单笔明细：与运单 Excel 模板保持同一字段口径。 */
@Data
public class BatchShipItemDTO {

    @NotNull(message = "缺少订单 ID")
    private Long orderId;

    @NotBlank(message = "请填写承运商编码")
    private String carrierCode;

    @NotBlank(message = "请填写运单号")
    private String waybillNo;
}
