package com.example.BackendArchitectureLab.Dto.Vo.Search;

import com.example.BackendArchitectureLab.Dto.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "功能搜尋查詢參數")
public class FunctionSearchQuery extends BaseSearchQuery {
    
    @Schema(description = "功能名稱（模糊查詢）", example = "使用者管理")
    private String name;
    
    @Schema(description = "父功能ID（精確查詢）", example = "uuid")
    private String parent;
    
    @Schema(description = "功能類型（精確查詢）", example = "1")
    private Integer type;
    
    @Schema(description = "創建者（精確查詢）", example = "admin")
    private String createdBy;
}
