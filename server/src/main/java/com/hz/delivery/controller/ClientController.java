package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.common.Constants;
import com.hz.delivery.dto.OrderAddressDTO;
import com.hz.delivery.dto.OrderCreateDTO;
import com.hz.delivery.entity.Address;
import com.hz.delivery.entity.Carrier;
import com.hz.delivery.entity.GoodsPackage;
import com.hz.delivery.entity.Notice;
import com.hz.delivery.entity.Order;
import com.hz.delivery.service.*;
import com.hz.delivery.vo.ClientLoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * C 端业务接口（需登录）
 */
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
public class ClientController {

    private final AuthService authService;
    private final PackageService packageService;
    private final OrderService orderService;
    private final AddressService addressService;
    private final NoticeService noticeService;
    private final ShipmentService shipmentService;

    /* ---------- 个人信息 ---------- */

    @GetMapping("/profile")
    public ApiResult<ClientLoginVO> profile(@RequestAttribute(Constants.ATTR_GRANTEE_ID) Long granteeId) {
        return ApiResult.ok(authService.profile(granteeId));
    }

    /* ---------- 套餐 ---------- */

    @GetMapping("/packages")
    public ApiResult<List<GoodsPackage>> packages() {
        return ApiResult.ok(packageService.listForClient());
    }

    @GetMapping("/packages/{id}")
    public ApiResult<GoodsPackage> packageDetail(@PathVariable Long id) {
        return ApiResult.ok(packageService.detail(id));
    }

    /* ---------- 公告 ---------- */

    @GetMapping("/notices")
    public ApiResult<List<Notice>> notices() {
        return ApiResult.ok(noticeService.listPublic());
    }

    /* ---------- 承运商 ---------- */

    /**
     * 可选承运商列表。
     * 包邮配送，用户在合作物流中自选；仅返回启用中的承运商。
     */
    @GetMapping("/carriers")
    public ApiResult<List<Carrier>> carriers() {
        return ApiResult.ok(shipmentService.carriers());
    }

    /* ---------- 地址簿 ---------- */

    @GetMapping("/addresses")
    public ApiResult<List<Address>> addresses(@RequestAttribute(Constants.ATTR_GRANTEE_ID) Long granteeId) {
        return ApiResult.ok(addressService.list(granteeId));
    }

    @PostMapping("/addresses")
    public ApiResult<Address> saveAddress(@RequestAttribute(Constants.ATTR_GRANTEE_ID) Long granteeId,
                                          @RequestBody Address address) {
        return ApiResult.ok(addressService.save(granteeId, address));
    }

    @DeleteMapping("/addresses/{id}")
    public ApiResult<Void> deleteAddress(@RequestAttribute(Constants.ATTR_GRANTEE_ID) Long granteeId,
                                         @PathVariable Long id) {
        addressService.delete(granteeId, id);
        return ApiResult.ok();
    }

    /* ---------- 订单 ---------- */

    /**
     * 提交配送信息
     */
    @PostMapping("/orders")
    public ApiResult<Order> createOrder(@RequestAttribute(Constants.ATTR_GRANTEE_ID) Long granteeId,
                                        @RequestBody @Valid OrderCreateDTO dto) {
        return ApiResult.ok(orderService.create(granteeId, dto));
    }

    @GetMapping("/orders")
    public ApiResult<List<Order>> myOrders(@RequestAttribute(Constants.ATTR_GRANTEE_ID) Long granteeId,
                                           @RequestParam(required = false) Integer status) {
        return ApiResult.ok(orderService.listByGrantee(granteeId, status));
    }

    @GetMapping("/orders/{id}")
    public ApiResult<Order> orderDetail(@RequestAttribute(Constants.ATTR_GRANTEE_ID) Long granteeId,
                                        @PathVariable Long id) {
        return ApiResult.ok(orderService.detail(granteeId, id));
    }

    /**
     * 按运单号查询物流进度（仅限本人订单）
     */
    @GetMapping("/orders/track")
    public ApiResult<Order> trackByWaybill(@RequestAttribute(Constants.ATTR_GRANTEE_ID) Long granteeId,
                                           @RequestParam String waybillNo) {
        return ApiResult.ok(orderService.trackByWaybill(granteeId, waybillNo));
    }

    @PostMapping("/orders/{id}/cancel")
    public ApiResult<Void> cancelOrder(@RequestAttribute(Constants.ATTR_GRANTEE_ID) Long granteeId,
                                       @PathVariable Long id,
                                       @RequestBody(required = false) Map<String, String> body) {
        orderService.cancel(granteeId, id, body == null ? null : body.get("reason"));
        return ApiResult.ok();
    }

    /**
     * 修改收货地址：仅「待发货」可改，且仅可自助修改 1 次。
     * 发货后地址已随运单下发承运商，自行修改会造成丢件，故一律拒绝并提示联系客服。
     */
    @PostMapping("/orders/{id}/address")
    public ApiResult<Order> updateOrderAddress(@RequestAttribute(Constants.ATTR_GRANTEE_ID) Long granteeId,
                                               @PathVariable Long id,
                                               @RequestBody @Valid OrderAddressDTO dto) {
        return ApiResult.ok(orderService.updateAddress(granteeId, id, dto));
    }

    /**
     * 地址预校验：前端提交前先探一次，减少无效提交
     */
    @PostMapping("/addresses/validate")
    public ApiResult<Map<String, Object>> validateAddress(@RequestBody OrderCreateDTO dto) {
        String err = AddressValidator.validate(dto.getReceiver(), dto.getPhone(),
                dto.getProvince(), dto.getCity(), dto.getDistrict(), dto.getDetail());
        return ApiResult.ok(Map.of("valid", err == null, "message", err == null ? "" : err));
    }
}
