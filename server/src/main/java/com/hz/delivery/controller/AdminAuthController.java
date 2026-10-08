package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.common.Constants;
import com.hz.delivery.dto.AdminLoginDTO;
import com.hz.delivery.dto.AdminChangePasswordDTO;
import com.hz.delivery.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@RequestBody @Valid AdminLoginDTO dto,
                                                 HttpServletRequest request) {
        return ApiResult.ok(adminAuthService.login(dto.getUsername(), dto.getPassword(), request.getRemoteAddr()));
    }

    @GetMapping("/profile")
    public ApiResult<Map<String, Object>> profile(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId) {
        return ApiResult.ok(adminAuthService.profile(adminId));
    }

    @PostMapping("/change-password")
    public ApiResult<Void> changePassword(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId,
                                          @RequestBody @Valid AdminChangePasswordDTO dto) {
        adminAuthService.changePassword(adminId, dto.getCurrentPassword(), dto.getNewPassword());
        return ApiResult.ok();
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestHeader(value = Constants.HEADER_TOKEN, required = false) String token) {
        adminAuthService.logout(token);
        return ApiResult.ok();
    }
}
