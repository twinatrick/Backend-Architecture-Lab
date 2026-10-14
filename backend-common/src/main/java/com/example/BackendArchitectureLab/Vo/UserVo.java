package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@ApiDto(description = "使用者資料傳輸物件")
public class UserVo {

    @ApiField(description = "使用者唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid")
    private String id;

    @ApiField(description = "使用者姓名", example = "王小明", required = true)
    private String name;

    @ApiField(description = "電子郵件信箱", example = "admin@example.com", required = true)
    private String email;

    @ApiField(description = "使用者密碼", example = "password123")
    private String password;

    @ApiField(description = "聯絡電話", example = "0912345678")
    private String phone;

    @ApiField(description = "帳號是否停用", example = "false")
    private boolean disabled;

    @ApiField(description = "建立者識別碼", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;

    @ApiField(description = "更新者識別碼", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String updatedBy;

    @ApiField(description = "建立時間", example = "2026-10-13T10:00:00.000Z")
    private Date createdTime;

    @ApiField(description = "更新時間", example = "2026-10-13T10:00:00.000Z")
    private Date updatedTime;

    @ApiField(description = "角色識別碼清單", example = "[\"4e28569a-055e-459e-acd7-d76f014fd801\"]")
    private List<String> roles;

    @ApiField(description = "角色名稱清單", example = "[\"admin\", \"user\"]")
    private List<String> roleNames;

    @ApiField(description = "權限功能清單")
    private List<FunctionVo> permissions;
}
