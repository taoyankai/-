package com.hz.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改收货地址（未发货前自助修改，限 1 次）。
 *
 * <p>刻意与下单入参 {@link OrderCreateDTO} 分开：改址不允许变更套餐与承运商，
 * 若复用下单 DTO，{@code packageId} 的必填校验会让改址请求直接 400，
 * 也会让接口语义变得含糊（传了套餐却不起作用）。
 */
@Data
public class OrderAddressDTO {

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

    private String remark;
}
