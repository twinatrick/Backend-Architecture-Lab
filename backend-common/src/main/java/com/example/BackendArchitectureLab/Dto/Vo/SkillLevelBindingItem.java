package com.example.BackendArchitectureLab.Dto.Vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SkillLevelBindingItem {
    @NotBlank(message = "skillId must not be blank")
    private String skillId;

    @NotBlank(message = "skillLevelId must not be blank")
    private String skillLevelId;
}
