package com.hz.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminLoginDTO {

    @NotBlank(message = "请填写账号")
    private String username;

    @NotBlank(message = "请填写密码")
    private String password;
}
