package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.common.Constants;
import com.hz.delivery.common.ExcelUtil;
import com.hz.delivery.common.PageResult;
import com.hz.delivery.entity.Grantee;
import com.hz.delivery.entity.ImportBatch;
import com.hz.delivery.excel.GranteeExcelRow;
import com.hz.delivery.excel.GranteeExportRow;
import com.hz.delivery.service.AdminAuthService;
import com.hz.delivery.service.GranteeService;
import com.hz.delivery.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 后台 - 教职工名单管理
 * 名单来源：甲方提供 Excel → 在此导入；支持按条件筛选后导出。
 */
@RestController
@RequestMapping("/api/admin/grantees")
@RequiredArgsConstructor
public class AdminGranteeController {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private final GranteeService granteeService;
    private final AdminAuthService adminAuthService;
    private final OperationLogService operationLogService;

    /**
     * 名单分页查询
     */
    @GetMapping
    public ApiResult<PageResult<Grantee>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String org,
                                               @RequestParam(required = false) Integer claimStatus) {
        return ApiResult.ok(granteeService.page(page, size, keyword, org, claimStatus));
    }

    /**
     * 下载导入模板（甲方按此模板提供名单）
     */
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        ExcelUtil.writeTemplate(response, "慰问品领取名单导入模板", "名单", GranteeExcelRow.class);
    }

    /**
     * 导入名单
     *
     * @param updateExists 手机号已存在时是否覆盖（默认 true）
     */
    @PostMapping("/import")
    public ApiResult<Map<String, Object>> importExcel(
            @RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") Boolean updateExists) {
        String operator = adminAuthService.operatorName(adminId);
        String fileName = file == null ? null : file.getOriginalFilename();
        try {
            Map<String, Object> r = granteeService.importExcel(file, operator, updateExists);
            operationLogService.success(adminId, "grantee", "import", "教职工名单导入",
                    "文件=" + fileName + "；覆盖已存在=" + updateExists
                            + "；新增=" + r.get("insertCount") + "；更新=" + r.get("updateCount"),
                    r.get("total") == null ? null : ((Number) r.get("total")).intValue(), request);
            return ApiResult.ok(r);
        } catch (RuntimeException e) {
            operationLogService.fail(adminId, "grantee", "import", "教职工名单导入",
                    "文件=" + fileName, e.getMessage(), request);
            throw e;
        }
    }

    /**
     * 导出名单：支持按关键词 / 单位 / 领取状态筛选后导出。
     * 导出文件含教职工姓名与手机号，属个人信息，必须留审计痕迹（谁、何时、从哪个 IP、什么范围）。
     */
    @GetMapping("/export")
    public void export(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
                       HttpServletRequest request,
                       HttpServletResponse response,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String org,
                       @RequestParam(required = false) Integer claimStatus,
                       @RequestParam(required = false) String scope) throws IOException {
        String cond = "关键词=" + (keyword == null || keyword.isEmpty() ? "无" : keyword)
                + "；单位=" + (org == null || org.isEmpty() ? "全部" : org)
                + "；领取状态=" + (claimStatus == null ? "全部" : claimStatus)
                + "；范围=" + (scope == null || scope.isEmpty() ? "全部" : scope);
        // scope=selected 时由前端传入 ids
        List<GranteeExportRow> rows = granteeService.buildExportRows(keyword, org, claimStatus);
        String name = "慰问品领取名单_" + LocalDateTime.now().format(STAMP);
        if (org != null && !org.isEmpty()) {
            name = "慰问品领取名单_" + org + "_" + LocalDateTime.now().format(STAMP);
        }
        try {
            ExcelUtil.write(response, name, "名单", GranteeExportRow.class, rows);
            operationLogService.success(adminId, "grantee", "export", "慰问品领取名单", cond, rows.size(), request);
        } catch (IOException e) {
            operationLogService.fail(adminId, "grantee", "export", "慰问品领取名单", cond, e.getMessage(), request);
            throw e;
        }
    }

    /**
     * 新增 / 修改教职工
     */
    @PostMapping
    public ApiResult<Grantee> save(@RequestBody Grantee grantee) {
        return ApiResult.ok(granteeService.save(grantee));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        granteeService.delete(id);
        return ApiResult.ok();
    }

    /**
     * 重置领取额度（误操作回退）
     */
    @PostMapping("/{id}/reset-quota")
    public ApiResult<Void> resetQuota(@PathVariable Long id) {
        granteeService.resetQuota(id);
        return ApiResult.ok();
    }

    /**
     * 导入批次记录
     */
    @GetMapping("/batches")
    public ApiResult<List<ImportBatch>> batches() {
        return ApiResult.ok(granteeService.batches("grantee"));
    }
}
