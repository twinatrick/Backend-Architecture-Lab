package com.example.BackendArchitectureLab.Dto.Query;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Dto.Query.BaseSearchQuery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Function 搜尋查詢參數
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "功能搜尋查詢參數")
public class FunctionSearchQuery extends BaseSearchQuery {
    
    @ApiField(description = "功能名稱（模糊查詢）", example = "使用者管理")
    private String name;
    
    @ApiField(description = "父功能ID（精確查詢）", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private String parent;
    
    @ApiField(description = "功能類型（精確查詢）", example = "1")
    private Integer type;
    
    @ApiField(description = "創建者 UUID（精確查詢）", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
}
