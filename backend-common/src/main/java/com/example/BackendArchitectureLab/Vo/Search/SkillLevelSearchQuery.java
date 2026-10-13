package com.example.BackendArchitectureLab.Vo.Search;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Vo.Common.BaseSearchQuery;
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
@ApiDto(description = "技能等級搜尋查詢參數")
public class SkillLevelSearchQuery extends BaseSearchQuery {
    
    @ApiField(description = "技能ID（精確查詢）", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private UUID skillId;
    
    @ApiField(description = "等級數值（精確查詢）", example = "3")
    private Integer levelValue;
    
    @ApiField(description = "等級標題（模糊查詢）", example = "高級")
    private String title;
    
    @ApiField(description = "等級描述（模糊查詢）", example = "精通")
    private String description;
    
    @ApiField(description = "創建者 UUID（精確查詢）", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
}
