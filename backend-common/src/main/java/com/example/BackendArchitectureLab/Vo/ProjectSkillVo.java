package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "專案綁定的技能與詳細等級資訊")
public class ProjectSkillVo {

    @ApiField(description = "專案 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private UUID projectId;

    @ApiField(description = "技能 UUID", example = "6ba7b810-9dad-11d1-80b4-00c04fd430c8", format = "uuid")
    private UUID skillId;

    @ApiField(description = "技能名稱", example = "Java")
    private String skillName;

    @ApiField(description = "技能描述", example = "後端主力語言")
    private String skillDescription;

    @ApiField(description = "技能等級 UUID", example = "7ca7b810-9dad-11d1-80b4-00c04fd430c9", format = "uuid")
    private UUID skillLevelId;

    @ApiField(description = "技能等級數值", example = "3")
    private Integer levelValue;

    @ApiField(description = "技能等級標題", example = "資深")
    private String levelTitle;

    @ApiField(description = "技能等級描述", example = "具備架構規劃能力")
    private String levelDescription;
}
