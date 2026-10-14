package com.example.BackendArchitectureLab.Vo;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiField;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ApiDto(description = "專案成員技能綁定清單項目")
public class MemberSkillBindings {
    @ApiField(description = "成員使用者 UUID", example = "550e8400-e29b-41d4-a716-446655440000", format = "uuid", required = true)
    @NotBlank(message = "userId must not be blank")
    private String userId;

    @ApiField(description = "成員技能等級清單")
    private @Valid List<@Valid SkillLevelBindingItem> skills;
}
