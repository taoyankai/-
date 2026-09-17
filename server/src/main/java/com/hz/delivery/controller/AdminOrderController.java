package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.common.Constants;
import com.hz.delivery.common.ExcelUtil;
import com.hz.delivery.common.PageResult;
import com.hz.delivery.dto.ShipDTO;
import com.hz.delivery.entity.Carrier;
import com.hz.delivery.entity.Order;
import com.hz.delivery.entity.OrderTrack;
import com.hz.delivery.excel.OrderExportRow;
import com.hz.delivery.excel.WaybillExcelRow;
import com.hz.delivery.service.AdminAuthService;
import com.hz.delivery.service.OperationLogService;
import com.hz.delivery.service.ShipmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 后台 - 订单汇总 / 发货管理 / 物流跟踪 / SLA 监控 / 报表导出
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private final ShipmentService shipmentService;
    private final AdminAuthService adminAuthService;
    private final OperationLogService operationLogService;

    /* ---------- 查询 ---------- */

    @GetMapping
    public ApiResult<PageResult<Order>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(required = false) String org,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer slaLevel) {
        return ApiResult.ok(shipmentService.pageOrders(page, size, status, org, keyword, slaLevel));
    }

    @GetMapping("/{id}")
    public ApiResult<Order> detail(@PathVariable Long id) {
        return ApiResult.ok(shipmentService.detail(id));
    }

    @GetMapping("/{id}/tracks")
    public ApiResult<List<OrderTrack>> tracks(@PathVariable Long id) {
        return ApiResult.ok(shipmentService.tracks(id));
    }

    /**
     * 批量取轨迹：物流跟踪页一次加载多单的最新轨迹。
     * 注意路由需早于 /{id}/tracks 的变量匹配，故使用 /tracks/batch。
     */
    @GetMapping("/tracks/batch")
    public ApiResult<Map<String, List<OrderTrack>>> tracksBatch(@RequestParam(required = false) String ids) {
        List<Long> idList = new ArrayList<>();
        if (StringUtils.hasText(ids)) {
            for (String s : ids.split(",")) {
                String v = s.trim();
                if (v.isEmpty()) {
                    continue;
                }
                try {
                    idList.add(Long.valueOf(v));
                } catch (NumberFormatException ignore) {
                    // 非法 ID 直接忽略，不影响其余订单
                }
            }
        }
        return ApiResult.ok(shipmentService.tracksBatch(idList));
    }

    /**
     * 承运商字典
     */
    @GetMapping("/carriers")
    public ApiResult<List<Carrier>> carriers() {
        return ApiResult.ok(shipmentService.carriers());
    }

    /**
     * SLA 监控：待发货 + 预警等级
     */
    @GetMapping("/sla")
    public ApiResult<PageResult<Order>> sla(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) Integer level,
                                            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(shipmentService.pageOrders(page, size, Constants.ORDER_PENDING, null, keyword, level));
    }

    /* ---------- 发货 ---------- */

    /**
     * 发货录入（支持批量，未指定运单号时按承运商规则自动生成）
     */
    @PostMapping("/ship")
    public ApiResult<Map<String, Object>> ship(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
                                               @RequestBody @Valid ShipDTO dto) {
        String operator = adminAuthService.operatorName(adminId);
        return ApiResult.ok(shipmentService.ship(dto, operator));
    }

    /**
     * 撤销发货（仅已发货状态，用于纠正录入错误）
     */
    @PostMapping("/{id}/revert-ship")
    public ApiResult<Void> revertShip(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, String> body) {
        shipmentService.revertShip(id, body == null ? null : body.get("reason"));
        return ApiResult.ok();
    }

    /**
     * 运单号批量导入模板
     */
    @GetMapping("/waybill-template")
    public void waybillTemplate(HttpServletResponse response) throws IOException {
        ExcelUtil.writeTemplate(response, "运单号批量导入模板", "运单", WaybillExcelRow.class);
    }

    /**
     * 运单号批量导入
     */
    @PostMapping("/import-waybill")
    public ApiResult<Map<String, Object>> importWaybill(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
                                                        @RequestParam("file") MultipartFile file) {
        String operator = adminAuthService.operatorName(adminId);
        return ApiResult.ok(shipmentService.importWaybills(file, operator));
    }

    /* ---------- 物流 ---------- */

    /**
     * 推进物流状态（真实项目由承运商回调驱动，此处支持人工补录）
     */
    @PostMapping("/{id}/push-status")
    public ApiResult<Void> pushStatus(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        Integer status = body.get("status") == null ? null : Integer.valueOf(String.valueOf(body.get("status")));
        String desc = body.get("description") == null ? null : String.valueOf(body.get("description"));
        if (status == null || desc == null) {
            return ApiResult.fail(com.hz.delivery.common.ErrorCode.PARAM_ERROR, "请填写状态与轨迹说明");
        }
        shipmentService.pushStatus(id, status, desc, adminAuthService.operatorName(adminId));
        return ApiResult.ok();
    }

    /**
     * 登记超时发货原因（写入订单异常说明 + 轨迹留痕）
     */
    @PostMapping("/{id}/delay-reason")
    public ApiResult<Void> delayReason(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
                                       @PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        String type = body.get("type") == null ? null : String.valueOf(body.get("type"));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        String eta = body.get("eta") == null ? null : String.valueOf(body.get("eta"));
        shipmentService.markDelay(id, type, note, eta, adminAuthService.operatorName(adminId));
        return ApiResult.ok();
    }

    /* ---------- 导出 ---------- */

    /**
     * 订单导出：与列表筛选条件一致。
     * 导出内容含教职工姓名、手机号与住址，属个人信息，必须留审计痕迹。
     */
    @GetMapping("/export")
    public void export(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
                       HttpServletRequest request,
                       HttpServletResponse response,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(required = false) String org,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer slaLevel) throws IOException {
        String cond = "状态=" + (status == null ? "全部" : status)
                + "；单位=" + (StringUtils.hasText(org) ? org : "全部")
                + "；关键词=" + (StringUtils.hasText(keyword) ? keyword : "无")
                + "；SLA档位=" + (slaLevel == null ? "全部" : slaLevel);
        List<OrderExportRow> rows = shipmentService.buildExportRows(status, org, keyword, slaLevel);
        String name = "配送订单明细_" + LocalDateTime.now().format(STAMP);
        try {
            ExcelUtil.write(response, name, "订单", OrderExportRow.class, rows);
            operationLogService.success(adminId, "order", "export", "配送订单明细", cond, rows.size(), request);
        } catch (IOException e) {
            operationLogService.fail(adminId, "order", "export", "配送订单明细", cond, e.getMessage(), request);
            throw e;
        }
    }

    /**
     * 待发货清单导出（用于线下安排发货）
     */
    @GetMapping("/export-pending")
    public void exportPending(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              @RequestParam(required = false) String org) throws IOException {
        String cond = "状态=待发货；单位=" + (StringUtils.hasText(org) ? org : "全部");
        List<OrderExportRow> rows = shipmentService.buildExportRows(Constants.ORDER_PENDING, org, null, null);
        try {
            ExcelUtil.write(response, "待发货清单_" + LocalDateTime.now().format(STAMP),
                    "待发货", OrderExportRow.class, rows);
            operationLogService.success(adminId, "order", "export-pending", "待发货清单", cond, rows.size(), request);
        } catch (IOException e) {
            operationLogService.fail(adminId, "order", "export-pending", "待发货清单", cond, e.getMessage(), request);
            throw e;
        }
    }
}
