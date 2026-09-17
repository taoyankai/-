package com.hz.delivery.controller;

import com.hz.delivery.common.ApiResult;
import com.hz.delivery.common.Constants;
import com.hz.delivery.dto.ClientLoginDTO;
import com.hz.delivery.service.AuthService;
import com.hz.delivery.vo.ClientLoginVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * C 端身份核验（免登录接口）
 */
@RestController
@RequestMapping("/api/client/auth")
@RequiredArgsConstructor
@Validated
public class ClientAuthController {

    private final AuthService authService;

    /**
     * 发送验证码：名单外手机号会被直接拦截。
     * 仅在 LOGIN_MODE=sms 时可用；免短信登录方式下返回 1007 提示。
     */
    @PostMapping("/send-code")
    public ApiResult<Map<String, Object>> sendCode(
            @RequestParam @NotBlank(message = "请填写手机号")
            @Pattern(regexp = "^1\\d{10}$", message = "请填写正确的 11 位手机号") String phone) {
        return ApiResult.ok(authService.sendCode(phone));
    }

    /**
     * 当前登录方式（免登录接口）。
     * C 端登录页据此决定展示「姓名」还是「短信验证码」，无需在前端硬编码。
     */
    @GetMapping("/mode")
    public ApiResult<Map<String, Object>> mode() {
        return ApiResult.ok(authService.loginModeInfo());
    }

    /**
     * 校验名单（不发送短信，用于前端实时提示）
     */
    @GetMapping("/check")
    public ApiResult<Map<String, Object>> check(
            @RequestParam @NotBlank(message = "请填写手机号") String phone) {
        // 复用发送逻辑之外的最小校验：仅判断是否在名单内
        return ApiResult.ok(authService.checkPhone(phone));
    }

    @PostMapping("/login")
    public ApiResult<ClientLoginVO> login(@RequestBody @Valid ClientLoginDTO dto) {
        return ApiResult.ok(authService.login(dto.getPhone(), dto.getCode(), dto.getName()));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestHeader(value = Constants.HEADER_TOKEN, required = false) String token) {
        authService.logout(token);
        return ApiResult.ok();
    }
}
