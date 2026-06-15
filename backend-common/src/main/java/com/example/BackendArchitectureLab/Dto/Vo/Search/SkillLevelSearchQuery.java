package com.example.BackendArchitectureLab.Dto.Vo.Search;

import com.example.BackendArchitectureLab.Dto.Vo.Common.BaseSearchQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * SkillLevel 搜尋查詢參數
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "技能等級搜尋查詢參數")
public class SkillLevelSearchQuery extends BaseSearchQuery {
    
    @Schema(description = "技能ID（精確查詢）", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID skillId;
    
    @Schema(description = "等級數值（精確查詢）", example = "3")
    private Integer levelValue;
    
    @Schema(description = "等級標題（模糊查詢）", example = "高級")
    private String title;
    
    @Schema(description = "等級描述（模糊查詢）", example = "精通")
    private String description;
    
    @Schema(description = "創建者（精確查詢）", example = "admin")
    private String createdBy;
}
