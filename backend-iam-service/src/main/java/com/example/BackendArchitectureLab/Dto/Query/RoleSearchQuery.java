package com.example.BackendArchitectureLab.Dto.Query;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Dto.Query.BaseSearchQuery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Role 搜尋查詢參數
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "角色搜尋查詢參數")
public class RoleSearchQuery extends BaseSearchQuery {
    
    @ApiField(description = "角色名稱（模糊查詢）", example = "管理員")
    private String name;
    
    @ApiField(description = "角色描述（模糊查詢）", example = "系統管理")
    private String description;
    
    @ApiField(description = "創建者 UUID（精確查詢）", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
}
