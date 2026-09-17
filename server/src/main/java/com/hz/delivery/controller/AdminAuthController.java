package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.common.Constants;
import com.hz.delivery.dto.AdminLoginDTO;
import com.hz.delivery.service.AdminAuthService;
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
    public ApiResult<Map<String, Object>> login(@RequestBody @Valid AdminLoginDTO dto) {
        return ApiResult.ok(adminAuthService.login(dto.getUsername(), dto.getPassword()));
    }

    @GetMapping("/profile")
    public ApiResult<Map<String, Object>> profile(@RequestAttribute(Constants.ATTR_ADMIN_ID) Long adminId) {
        return ApiResult.ok(adminAuthService.profile(adminId));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestHeader(value = Constants.HEADER_TOKEN, required = false) String token) {
        adminAuthService.logout(token);
        return ApiResult.ok();
    }
}
