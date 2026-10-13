package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "功能與權限節點資料傳輸物件")
public class FunctionVo {
    @ApiField(description = "功能節點 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private String id;

    @ApiField(description = "功能名稱", example = "使用者管理")
    private String name = "";

    @ApiField(description = "父層功能 UUID", example = "6ba7b810-9dad-11d1-80b4-00c04fd430c8", format = "uuid")
    private String parent = "";

    @ApiField(description = "排序值", example = "1")
    private String sort = "";

    @ApiField(description = "節點層級類別 (1: 模組, 2: 頁面, 3: 按鈕)", example = "1")
    private Integer type;

    @ApiField(description = "父層功能名稱", example = "系統管理")
    private String parentName = "";

    @ApiField(description = "祖父層功能 UUID", example = "6ba7b810-9dad-11d1-80b4-00c04fd430c9", format = "uuid")
    private String grandParentId = "";

    @ApiField(description = "是否已停用", example = "false")
    private boolean disabled;

    @ApiField(description = "是否允許編輯", example = "true")
    private boolean edit;

    @ApiField(description = "是否為新建立節點", example = "false")
    private boolean newAdd;

    @ApiField(description = "異動後之新名稱", example = "使用者管理")
    private String newName = "";

    @ApiField(description = "是否標記為刪除", example = "false")
    private boolean delete;
}
