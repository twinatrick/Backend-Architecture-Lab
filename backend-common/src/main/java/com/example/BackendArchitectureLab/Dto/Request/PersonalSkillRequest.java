package com.example.BackendArchitectureLab.Dto.Request;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 個人技能請求 Vo
 * 用於一般使用者新增或修改個人技能
 */
@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "個人技能新增與異動請求物件")
public class PersonalSkillRequest {
    @ApiField(description = "技能名稱", example = "Java", required = true)
    private String name;

    @ApiField(description = "技能描述", example = "後端主力語言")
    private String description;

    @ApiField(description = "技能等級 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private String skillLevelId;

    @ApiField(description = "手動建立等級值", example = "3")
    private Integer skillLevelValue;

    @ApiField(description = "手動建立等級標題", example = "資深")
    private String skillLevelTitle;

    @ApiField(description = "手動建立等級描述", example = "具備架構規劃能力")
    private String skillLevelDescription;
}
