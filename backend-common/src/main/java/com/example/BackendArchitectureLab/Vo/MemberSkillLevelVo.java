package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "成員單項技能等級數值物件")
public class MemberSkillLevelVo {
    @ApiField(description = "技能 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid")
    private String skillId;

    @ApiField(description = "技能名稱", example = "Java")
    private String skillName;

    @ApiField(description = "技能等級 UUID", example = "6ba7b810-9dad-11d1-80b4-00c04fd430c8", format = "uuid")
    private String skillLevelId;

    @ApiField(description = "技能等級標題", example = "資深")
    private String levelTitle;

    @ApiField(description = "技能等級數值", example = "3")
    private Integer levelValue;
}
