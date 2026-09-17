package com.hz.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收货地址簿
 */
@Data
@TableName("t_address")
public class Address {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long granteeId;

    private String name;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;

    /** 是否允许放代收点，默认 false */
    private Boolean allowStation;

    private Boolean isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
