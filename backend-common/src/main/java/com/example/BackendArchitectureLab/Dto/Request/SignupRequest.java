package com.example.BackendArchitectureLab.Dto.Request;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "使用者註冊請求物件")
public class SignupRequest {
    @ApiField(description = "電子郵件信箱", example = "newuser@example.com", required = true)
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Invalid email format")
    private String email;

    @ApiField(description = "使用者密碼", example = "P@ssw0rd123", required = true)
    @NotBlank(message = "Password must not be blank")
    private String password;
}
