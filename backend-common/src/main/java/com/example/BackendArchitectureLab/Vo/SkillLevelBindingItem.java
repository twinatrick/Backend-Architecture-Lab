package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "技能等級綁定項目")
public class SkillLevelBindingItem {
    @ApiField(description = "技能 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid", required = true)
    @NotBlank(message = "skillId must not be blank")
    private String skillId;

    @ApiField(description = "技能等級 UUID", example = "550e8400-e29b-41d4-a716-446655440001", format = "uuid", required = true)
    @NotBlank(message = "skillLevelId must not be blank")
    private String skillLevelId;
}
