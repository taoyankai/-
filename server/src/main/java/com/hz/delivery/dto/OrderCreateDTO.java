package com.hz.delivery.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交配送信息（下单）
 */
@Data
public class OrderCreateDTO {

    @NotNull(message = "请选择一个套餐")
    private Long packageId;

    @Min(value = 1, message = "领取份数至少为 1")
    private Integer quantity = 1;

    @NotBlank(message = "请填写收货人姓名")
    private String receiver;

    @NotBlank(message = "请填写联系电话")
    private String phone;

    @NotBlank(message = "请选择省份")
    private String province;

    @NotBlank(message = "请选择城市")
    private String city;

    @NotBlank(message = "请选择区县")
    private String district;

    @NotBlank(message = "请填写详细地址")
    private String detail;

    /** 是否允许投放代收点。招标要求默认不允许，前端开关默认关闭 */
    private Boolean allowStation = false;

    /** 期望承运商编码。包邮配送，用户在合作物流中自选；缺省取第一家启用的承运商 */
    private String carrierCode;

    private String remark;

    /** 是否将该地址存入地址簿 */
    private Boolean saveAddress = false;
}
