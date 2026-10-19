package com.example.BackendArchitectureLab.Dto.Request;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "建立超級使用者請求")
public class SuperUserRequest {

    @ApiField(description = "超級使用者初始化金鑰", example = "super-secret-key", required = true)
    private String bootstrapSecret;

    @ApiField(description = "超級使用者電子郵件信箱", example = "admin@example.com", required = true)
    private String email;
}
