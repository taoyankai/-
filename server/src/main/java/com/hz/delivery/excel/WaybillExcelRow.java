package com.hz.delivery.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 运单号批量导入行模型
 */
@Data
public class WaybillExcelRow {

    @ColumnWidth(24)
    @ExcelProperty(value = "订单号", index = 0)
    private String orderNo;

    @ColumnWidth(14)
    @ExcelProperty(value = "承运商编码", index = 1)
    private String carrierCode;

    @ColumnWidth(24)
    @ExcelProperty(value = "运单号", index = 2)
    private String waybillNo;
}
