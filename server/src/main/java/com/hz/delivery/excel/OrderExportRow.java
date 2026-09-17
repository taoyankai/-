package com.hz.delivery.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 订单导出模型
 */
@Data
public class OrderExportRow {

    @ColumnWidth(22)
    @ExcelProperty(value = "订单号", index = 0)
    private String orderNo;

    @ColumnWidth(12)
    @ExcelProperty(value = "工号", index = 1)
    private String empNo;

    @ColumnWidth(12)
    @ExcelProperty(value = "领取人", index = 2)
    private String granteeName;

    @ColumnWidth(24)
    @ExcelProperty(value = "单位", index = 3)
    private String org;

    @ColumnWidth(22)
    @ExcelProperty(value = "部门", index = 4)
    private String dept;

    @ColumnWidth(22)
    @ExcelProperty(value = "已选套餐", index = 5)
    private String packageName;

    @ColumnWidth(10)
    @ExcelProperty(value = "份数", index = 6)
    private Integer quantity;

    @ColumnWidth(12)
    @ExcelProperty(value = "收件人", index = 7)
    private String receiver;

    @ColumnWidth(16)
    @ExcelProperty(value = "收件电话", index = 8)
    private String phone;

    @ColumnWidth(46)
    @ExcelProperty(value = "配送地址", index = 9)
    private String address;

    @ColumnWidth(16)
    @ExcelProperty(value = "是否允许代收点", index = 10)
    private String allowStation;

    @ColumnWidth(14)
    @ExcelProperty(value = "订单状态", index = 11)
    private String statusName;

    @ColumnWidth(16)
    @ExcelProperty(value = "承运商", index = 12)
    private String carrierName;

    @ColumnWidth(22)
    @ExcelProperty(value = "运单号", index = 13)
    private String waybillNo;

    @ColumnWidth(20)
    @ExcelProperty(value = "提交时间", index = 14)
    private String createTime;

    @ColumnWidth(20)
    @ExcelProperty(value = "承诺发货截止", index = 15)
    private String slaDeadline;

    @ColumnWidth(20)
    @ExcelProperty(value = "发货时间", index = 16)
    private String shipTime;

    @ColumnWidth(16)
    @ExcelProperty(value = "实际发货时长(天)", index = 17)
    private String shipDays;

    @ColumnWidth(20)
    @ExcelProperty(value = "签收时间", index = 18)
    private String signTime;

    @ColumnWidth(30)
    @ExcelProperty(value = "备注", index = 19)
    private String remark;
}
