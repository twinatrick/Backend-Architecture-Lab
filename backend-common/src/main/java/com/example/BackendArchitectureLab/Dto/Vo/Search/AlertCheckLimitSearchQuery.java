package com.example.BackendArchitectureLab.Dto.Vo.Search;

import com.example.BackendArchitectureLab.Dto.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "告警檢查限制搜尋查詢參數")
public class AlertCheckLimitSearchQuery extends BaseSearchQuery {
    
    @Schema(description = "表名（模糊查詢）", example = "user")
    private String tableName;
    
    @Schema(description = "欄位名（模糊查詢）", example = "age")
    private String columnName;
    
    @Schema(description = "限制值最小值（範圍查詢）", example = "0.0")
    private Double limitValueMin;
    
    @Schema(description = "限制值最大值（範圍查詢）", example = "100.0")
    private Double limitValueMax;
    
    @Schema(description = "創建者（精確查詢）", example = "admin")
    private String createdBy;
}
