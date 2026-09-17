package com.hz.delivery.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 名单导出模型：教职工基础信息 + 领取情况 + 配送履约情况
 */
@Data
public class GranteeExportRow {

    @ColumnWidth(14)
    @ExcelProperty(value = "工号", index = 0)
    private String empNo;

    @ColumnWidth(12)
    @ExcelProperty(value = "姓名", index = 1)
    private String name;

    @ColumnWidth(16)
    @ExcelProperty(value = "手机号", index = 2)
    private String phone;

    @ColumnWidth(24)
    @ExcelProperty(value = "单位", index = 3)
    private String org;

    @ColumnWidth(22)
    @ExcelProperty(value = "部门", index = 4)
    private String dept;

    @ColumnWidth(12)
    @ExcelProperty(value = "可领份数", index = 5)
    private Integer quota;

    @ColumnWidth(12)
    @ExcelProperty(value = "已领份数", index = 6)
    private Integer used;

    @ColumnWidth(14)
    @ExcelProperty(value = "领取状态", index = 7)
    private String claimStatus;

    @ColumnWidth(22)
    @ExcelProperty(value = "已选套餐", index = 8)
    private String packageName;

    @ColumnWidth(22)
    @ExcelProperty(value = "订单号", index = 9)
    private String orderNo;

    @ColumnWidth(14)
    @ExcelProperty(value = "订单状态", index = 10)
    private String orderStatus;

    @ColumnWidth(46)
    @ExcelProperty(value = "配送地址", index = 11)
    private String address;

    @ColumnWidth(12)
    @ExcelProperty(value = "收件人", index = 12)
    private String receiver;

    @ColumnWidth(16)
    @ExcelProperty(value = "收件电话", index = 13)
    private String receiverPhone;

    @ColumnWidth(16)
    @ExcelProperty(value = "承运商", index = 14)
    private String carrierName;

    @ColumnWidth(22)
    @ExcelProperty(value = "运单号", index = 15)
    private String waybillNo;

    @ColumnWidth(20)
    @ExcelProperty(value = "提交时间", index = 16)
    private String createTime;

    @ColumnWidth(20)
    @ExcelProperty(value = "发货时间", index = 17)
    private String shipTime;

    @ColumnWidth(16)
    @ExcelProperty(value = "是否允许放代收点", index = 18)
    private String allowStation;

    @ColumnWidth(24)
    @ExcelProperty(value = "备注", index = 19)
    private String remark;
}
