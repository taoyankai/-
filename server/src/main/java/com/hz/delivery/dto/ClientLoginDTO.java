package com.hz.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClientLoginDTO {

    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^1\\d{10}$", message = "请填写正确的 11 位手机号")
    private String phone;

    /**
     * 姓名，仅免短信登录方式（LOGIN_MODE=name）时使用，须与甲方名单登记一致。
     */
    @Size(max = 20, message = "姓名长度不正确")
    private String name;

    /**
     * 短信验证码，仅 LOGIN_MODE=sms 时使用。
     */
    @Pattern(regexp = "^$|^\\d{4,6}$", message = "验证码格式不正确")
    private String code;
}
