package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Project 搜尋查詢參數
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "專案搜尋查詢參數")
public class ProjectSearchQuery extends BaseSearchQuery {
    
    @ApiField(description = "專案名稱（模糊查詢）", example = "電商系統")
    private String name;
    
    @ApiField(description = "專案描述（模糊查詢）", example = "線上購物")
    private String description;
    
    @ApiField(description = "創建者 UUID（精確查詢）", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
}
