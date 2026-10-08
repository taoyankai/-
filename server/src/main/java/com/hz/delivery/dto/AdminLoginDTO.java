package com.hz.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminLoginDTO {

    @NotBlank(message = "请填写账号")
    @Size(max = 32, message = "账号长度不能超过 32 位")
    private String username;

    @NotBlank(message = "请填写密码")
    @Size(max = 128, message = "密码长度不能超过 128 位")
    private String password;
}
