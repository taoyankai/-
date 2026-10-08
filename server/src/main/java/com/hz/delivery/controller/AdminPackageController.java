package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.common.Constants;
import com.hz.delivery.dto.PackageSaveDTO;
import com.hz.delivery.entity.GoodsPackage;
import com.hz.delivery.service.AdminAuthService;
import com.hz.delivery.service.OperationLogService;
import com.hz.delivery.service.PackageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/packages")
@RequiredArgsConstructor
public class AdminPackageController {

    private final PackageService packageService;
    private final AdminAuthService adminAuthService;
    private final OperationLogService operationLogService;

    @GetMapping
    public ApiResult<List<GoodsPackage>> list(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(packageService.listForAdmin(keyword));
    }

    @GetMapping("/{id}")
    public ApiResult<GoodsPackage> detail(@PathVariable Long id) {
        return ApiResult.ok(packageService.detail(id));
    }

    /**
     * 新增 / 修改套餐。招标要求不允许私自更改方案内容，修改必须填写变更原因。
     */
    @PostMapping
    public ApiResult<GoodsPackage> save(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
                                        @RequestBody @Valid PackageSaveDTO dto) {
        String operator = adminAuthService.operatorName(adminId);
        return ApiResult.ok(packageService.save(dto, operator));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
                                  @PathVariable Long id,
                                  @RequestParam String reason,
                                  HttpServletRequest request) {
        String operator = adminAuthService.operatorName(adminId);
        try {
            GoodsPackage deleted = packageService.delete(id, reason, operator);
            operationLogService.success(adminId, "package", "delete",
                    "套餐 " + deleted.getNo(), "原因=" + reason, 1, request);
            return ApiResult.ok();
        } catch (RuntimeException e) {
            operationLogService.fail(adminId, "package", "delete",
                    "套餐 ID " + id, "原因=" + reason, e.getMessage(), request);
            throw e;
        }
    }
}
