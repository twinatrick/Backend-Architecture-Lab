package com.example.BackendArchitectureLab.Dto.Query;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import com.example.BackendArchitectureLab.Dto.Query.BaseSearchQuery;
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
@ApiDto(description = "技能搜尋查詢參數")
public class SkillSearchQuery extends BaseSearchQuery {
    
    @ApiField(description = "技能名稱（模糊查詢）", example = "Java")
    private String name;
    
    @ApiField(description = "技能描述（模糊查詢）", example = "程式語言")
    private String description;
    
    @ApiField(description = "創建者 UUID（精確查詢）", example = "00000000-0000-0000-0000-000000000001", format = "uuid")
    private String createdBy;
}
