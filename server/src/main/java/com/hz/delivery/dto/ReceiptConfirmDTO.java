package com.hz.delivery.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 后台代确认收货，备注选填。 */
@Data
public class ReceiptConfirmDTO {
    @Size(max = 200, message = "备注最多200字")
    private String remark;
}
