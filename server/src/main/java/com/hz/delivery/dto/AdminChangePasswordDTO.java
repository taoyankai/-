package com.hz.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminChangePasswordDTO {

    @NotBlank(message = "请填写当前密码")
    private String currentPassword;

    @NotBlank(message = "请填写新密码")
    @Size(min = 12, max = 64, message = "新密码长度须为 12-64 位")
    private String newPassword;
}
