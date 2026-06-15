package com.example.BackendArchitectureLab.Dto.Vo.Search;

import com.example.BackendArchitectureLab.Dto.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "角色搜尋查詢參數")
public class RoleSearchQuery extends BaseSearchQuery {
    
    @Schema(description = "角色名稱（模糊查詢）", example = "管理員")
    private String name;
    
    @Schema(description = "角色描述（模糊查詢）", example = "系統管理")
    private String description;
    
    @Schema(description = "創建者（精確查詢）", example = "admin")
    private String createdBy;
}
