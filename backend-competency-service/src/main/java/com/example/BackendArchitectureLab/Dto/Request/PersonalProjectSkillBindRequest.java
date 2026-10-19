package com.example.BackendArchitectureLab.Dto.Request;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "個人專案技能綁定請求")
public class PersonalProjectSkillBindRequest {
    @ApiField(description = "技能 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid", required = true)
    private String skillId;

    @ApiField(description = "技能等級 UUID", example = "550e8400-e29b-41d4-a716-446655440001", format = "uuid", required = true)
    private String skillLevelId;
}
