package com.hz.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 名单 / 运单导入批次记录，便于追溯"这批名单是谁在什么时候导进来的"
 */
@Data
@TableName("t_import_batch")
public class ImportBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次号 */
    private String batchNo;

    /** 类型：grantee 名单 / waybill 运单号 */
    private String type;

    private String fileName;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;

    /** 失败明细（行号:原因），换行分隔 */
    private String failDetail;

    private String operator;

    private LocalDateTime createTime;
}
