package com.hz.delivery.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SLA 发货预警记录。
 *
 * <p>每天一条（每档），用于看板标红与「预警是否已通知」的追溯。
 */
@Data
@TableName("t_sla_alert")
public class SlaAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate alertDate;

    /** 3=已超期 2=24 小时内到期 1=72 小时内到期 */
    private Integer level;

    private Integer orderCount;

    /** 订单号清单（逗号分隔，最多 30 条） */
    private String orderNos;

    /** 是否已短信通知：1 是 0 否 */
    private Integer notified;

    /** 通知结果，保留短信平台返回原文便于排障 */
    private String notifyResp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
