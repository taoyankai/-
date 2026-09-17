package com.hz.delivery.common;

import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Excel 导出工具：统一处理中文文件名与响应头
 */
public final class ExcelUtil {

    private ExcelUtil() {}

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * 导出数据
     */
    public static void write(HttpServletResponse response, String fileName,
                             String sheetName, Class<?> headClass, List<?> data) throws IOException {
        prepare(response, fileName);
        EasyExcel.write(response.getOutputStream(), headClass)
                .sheet(sheetName)
                .doWrite(data == null ? Collections.emptyList() : data);
    }

    /**
     * 导出空模板（仅表头），供用户下载后填写
     */
    public static void writeTemplate(HttpServletResponse response, String fileName,
                                     String sheetName, Class<?> headClass) throws IOException {
        prepare(response, fileName);
        EasyExcel.write(response.getOutputStream(), headClass)
                .sheet(sheetName)
                .doWrite(Collections.emptyList());
    }

    private static void prepare(HttpServletResponse response, String fileName) {
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 中文文件名必须 URL 编码，否则浏览器下载后乱码
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded + ".xlsx");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }
}
