package com.example.BackendArchitectureLab.Dto.Request;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "個人技能等級設定請求")
public class PersonalSkillLevelRequest {
    @ApiField(description = "技能等級 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid", required = true)
    private String skillLevelId;
}
