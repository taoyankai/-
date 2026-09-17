package com.hz.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教职工名单（由甲方提供的 Excel 导入）
 */
@Data
@TableName("t_grantee")
public class Grantee {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工号 */
    private String empNo;

    /** 姓名 */
    private String name;

    /** 手机号（登录唯一标识） */
    private String phone;

    /** 所在单位 */
    private String org;

    /** 所在部门 */
    private String dept;

    /** 可领取份数（本项目默认 1，每人只能选择一个套餐） */
    private Integer quota;

    /** 已领取份数 */
    private Integer used;

    /** 状态：1 启用，0 停用 */
    private Integer status;

    /** 来源导入批次 */
    private Long batchId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
