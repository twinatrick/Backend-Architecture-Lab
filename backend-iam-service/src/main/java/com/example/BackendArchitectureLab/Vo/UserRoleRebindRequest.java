package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "重新綁定使用者角色請求")
public class UserRoleRebindRequest {

    @NotEmpty(message = "roleIds must not be empty")
    @ApiField(description = "目標角色識別碼清單", example = "[\"4e28569a-055e-459e-acd7-d76f014fd801\"]", required = true)
    private List<String> roleIds;
}
