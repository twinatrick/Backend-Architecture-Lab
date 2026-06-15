package com.example.BackendArchitectureLab.Dto.Vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProjectSkillRebindRequest {
    @NotBlank(message = "projectId must not be blank")
    private String projectId;

    @NotEmpty(message = "bindings must not be empty")
    private @Valid List<@Valid SkillLevelBindingItem> bindings;
}
