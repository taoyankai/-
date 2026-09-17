package com.hz.delivery.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 名单导入行模型：甲方提供的 Excel 按此模板整理后上传
 */
@Data
public class GranteeExcelRow {

    @ColumnWidth(16)
    @ExcelProperty(value = "工号", index = 0)
    private String empNo;

    @ColumnWidth(12)
    @ExcelProperty(value = "姓名", index = 1)
    private String name;

    @ColumnWidth(16)
    @ExcelProperty(value = "手机号", index = 2)
    private String phone;

    @ColumnWidth(26)
    @ExcelProperty(value = "单位", index = 3)
    private String org;

    @ColumnWidth(24)
    @ExcelProperty(value = "部门", index = 4)
    private String dept;

    @ColumnWidth(20)
    @ExcelProperty(value = "备注", index = 5)
    private String remark;
}
