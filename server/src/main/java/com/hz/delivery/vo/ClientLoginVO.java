package com.hz.delivery.vo;

import lombok.Data;

/**
 * C 端登录结果
 */
@Data
public class ClientLoginVO {

    private String token;

    private Long granteeId;

    private String empNo;

    private String name;

    private String phone;

    private String org;

    private String dept;

    /** 可领取份数（本项目为 1） */
    private Integer quota;

    /** 已领取份数 */
    private Integer used;

    /** 是否还有领取额度 */
    private Boolean canClaim;
}
