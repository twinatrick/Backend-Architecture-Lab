package com.example.BackendArchitectureLab.Dto.Vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class MemberSkillBindings {
    @NotBlank(message = "userId must not be blank")
    private String userId;

    private @Valid List<@Valid SkillLevelBindingItem> skills;
}
