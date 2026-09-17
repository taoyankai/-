package com.hz.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 承运商字典
 */
@Data
@TableName("t_carrier")
public class Carrier {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 编码，如 SF */
    private String code;

    /** 名称，如 顺丰速运 */
    private String name;

    /** 查询链接模板，{no} 会被替换为运单号 */
    private String trackUrl;

    /** 联系电话 */
    private String phone;

    private Integer sort;

    private Integer status;
}
