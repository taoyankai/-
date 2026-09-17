package com.hz.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志。
 *
 * <p>涉及个人信息的操作（尤其名单与订单导出）必须留痕：谁、什么时候、从哪个 IP、
 * 按什么条件、导出了多少条。既是招标文件对数据合规的明确要求，也是事后追溯的依据
 * —— 教职工名单含手机号与住址，一旦外泄需要能定位到具体操作。
 */
@Data
@TableName("t_operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adminId;
    private String adminAccount;
    private String adminName;
    private String adminRole;

    /** 模块：grantee 名单 / order 订单 / package 套餐 / notice 公告 / sms 短信 */
    private String module;

    /** 动作：export 导出 / import 导入 / delete 删除 / reset-quota 重置额度 */
    private String action;

    /** 操作对象或范围描述，如「慰问品领取名单」「配送订单明细」 */
    private String target;

    /** 明细：当时的筛选条件与影响条数，用于还原导出范围 */
    private String detail;

    /** 涉及记录数 */
    private Integer rowCount;

    private String ip;
    private String userAgent;

    /** success / fail */
    private String result;
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
