package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "角色資料呈現物件")
public class RoleVo {

    @ApiField(description = "角色唯一識別碼", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private UUID id;

    @ApiField(description = "角色名稱", example = "系統管理員", required = true)
    private String name;

    @ApiField(description = "角色描述", example = "擁有系統完整管理權限")
    private String description;

    @ApiField(description = "建立者識別碼", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;

    @ApiField(description = "最後更新者識別碼", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String updatedBy;

    @ApiField(description = "建立時間", example = "2026-10-13T10:00:00.000Z")
    private Date createdTime;

    @ApiField(description = "更新時間", example = "2026-10-13T10:00:00.000Z")
    private Date updatedTime;

    @ApiField(description = "擁有功能數量", example = "5")
    private Integer functionCount;

    @ApiField(description = "擁有功能識別碼清單", example = "[\"00000000-0000-0000-0000-000000000001\"]")
    private List<String> functionIds;
}
