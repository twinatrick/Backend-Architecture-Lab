package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "權限綁定與關聯操作資料傳輸物件")
public class PermissionVo {

    @ApiField(description = "單一使用者識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid")
    private String user;

    @ApiField(description = "使用者識別碼清單")
    private List<String> userList;

    @ApiField(description = "單一功能識別碼", example = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6", format = "uuid")
    private String function;

    @ApiField(description = "功能識別碼清單")
    private List<String> functionList;

    @ApiField(description = "單一角色識別碼", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d", format = "uuid")
    private String role;

    @ApiField(description = "角色識別碼清單")
    private List<String> roleList;
}
