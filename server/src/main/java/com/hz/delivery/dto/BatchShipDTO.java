package com.hz.delivery.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 多承运商批量发货请求。 */
@Data
public class BatchShipDTO {

    @Valid
    @NotEmpty(message = "请填写待发货明细")
    @Size(max = 100, message = "单次最多发货 100 笔")
    private List<BatchShipItemDTO> items;
}
