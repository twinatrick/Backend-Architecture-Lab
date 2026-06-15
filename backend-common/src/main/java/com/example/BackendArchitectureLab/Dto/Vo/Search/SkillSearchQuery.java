package com.example.BackendArchitectureLab.Dto.Vo.Search;

import com.example.BackendArchitectureLab.Dto.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Skill 搜尋查詢參數
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "技能搜尋查詢參數")
public class SkillSearchQuery extends BaseSearchQuery {
    
    @Schema(description = "技能名稱（模糊查詢）", example = "Java")
    private String name;
    
    @Schema(description = "技能描述（模糊查詢）", example = "程式語言")
    private String description;
    
    @Schema(description = "創建者（精確查詢）", example = "admin")
    private String createdBy;
}
