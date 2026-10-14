package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AlertCheckLimit 搜尋查詢參數
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "告警檢查限制搜尋查詢參數")
public class AlertCheckLimitSearchQuery extends BaseSearchQuery {
    
    @ApiField(description = "表名（模糊查詢）", example = "user")
    private String tableName;
    
    @ApiField(description = "欄位名（模糊查詢）", example = "age")
    private String columnName;
    
    @ApiField(description = "限制值最小值（範圍查詢）", example = "0.0")
    private Double limitValueMin;
    
    @ApiField(description = "限制值最大值（範圍查詢）", example = "100.0")
    private Double limitValueMax;
    
    @ApiField(description = "創建者 UUID（精確查詢）", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
}
