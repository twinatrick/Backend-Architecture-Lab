package com.example.BackendArchitectureLab.Dto.Vo.Search;

import com.example.BackendArchitectureLab.Dto.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "專案搜尋查詢參數")
public class ProjectSearchQuery extends BaseSearchQuery {
    
    @Schema(description = "專案名稱（模糊查詢）", example = "電商系統")
    private String name;
    
    @Schema(description = "專案描述（模糊查詢）", example = "線上購物")
    private String description;
    
    @Schema(description = "創建者（精確查詢）", example = "admin")
    private String createdBy;
}
